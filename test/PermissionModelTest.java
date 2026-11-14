import org.junit.Assert;
import org.junit.Test;

import models.forum.Category;
import models.forum.Permission;
import models.forum.UserGroup;

/**
 * Coverage of models.forum.Permission.
 *
 *   - The five access levels are the numbers forum/Forums and Profile.Security.check() compare against; view
 *     1, reply 2, post 3, moderate 4, administrate 5. Category.forUser() keeps every row above 0.
 *   - toString() is "group - accessLevel - category", the string the CRUD list of admin/Permissions prints.
 *   - findByUserGroupAndCategory() answers the first row of the pair, so two permissions for the same group
 *     and category cannot be told apart; the Admin group of test/data.yml has no row, so every lookup on it
 *     answers null.
 *   - The constructor only fills the three fields: it does not create the row, and the id stays null until
 *     create() runs. That is how admin/Permissions stores a new grant.
 *   - delete() removes a grant without touching the category or the group it pointed at: Permission is the
 *     child of both relations, so MySQL never refuses this DELETE.
 */
public class PermissionModelTest extends BaseModelTest {

	@Test
	public void theAccessLevelsAreTheFiveNumbersTheChecksCompare() throws Throwable {
		Assert.assertEquals("5", String.valueOf(Permission.administrate));
		Assert.assertEquals("4", String.valueOf(Permission.moderate));
		Assert.assertEquals("3", String.valueOf(Permission.post));
		Assert.assertEquals("2", String.valueOf(Permission.reply));
		Assert.assertEquals("1", String.valueOf(Permission.view));
	}

	@Test
	public void toStringIsGroupThenLevelThenCategory() throws Throwable {
		inTransactionVoid(() -> {
			Permission moderateMain = Permission.findByUserGroupAndCategory(userGroup(UserGroup.MODERATORS), category(2L));
			Assert.assertEquals("Moderators - 4 - Main Category", moderateMain.toString());
			Assert.assertEquals("Moderators", moderateMain.group.name);
			Assert.assertEquals("Main Category", moderateMain.category.name);
			Assert.assertEquals("4", String.valueOf(moderateMain.accessLevel));
			Assert.assertEquals("5", String.valueOf(Permission.count()));
			// Insertion order of test/data.yml: postMain, moderateMain, postModerators, viewModerators,
			// viewAnonymous.
			Assert.assertEquals("[General - 3 - Main Category, Moderators - 4 - Main Category, "
					+ "Moderators - 3 - Moderators Category, General - 1 - Moderators Category, "
					+ "Anonymous - 1 - Main Category]", names(Permission.findAll()));
			return null;
		});
	}

	@Test
	public void findByUserGroupAndCategoryResolvesEveryPairOfTheFixtures() throws Throwable {
		inTransactionVoid(() -> {
			Category main = category(2L);
			Category moderators = category(1L);

			Assert.assertEquals("Moderators - 4 - Main Category",
					String.valueOf(Permission.findByUserGroupAndCategory(userGroup(UserGroup.MODERATORS), main)));
			Assert.assertEquals("Moderators - 3 - Moderators Category",
					String.valueOf(Permission.findByUserGroupAndCategory(userGroup(UserGroup.MODERATORS), moderators)));
			Assert.assertEquals("General - 3 - Main Category",
					String.valueOf(Permission.findByUserGroupAndCategory(userGroup(UserGroup.GENERAL), main)));
			Assert.assertEquals("General - 1 - Moderators Category",
					String.valueOf(Permission.findByUserGroupAndCategory(userGroup(UserGroup.GENERAL), moderators)));
			Assert.assertEquals("Anonymous - 1 - Main Category",
					String.valueOf(Permission.findByUserGroupAndCategory(userGroup(UserGroup.ANONYMOUS), main)));

			// No Permission row points at the Admin group, so nothing resolves for it in either category.
			Assert.assertNull(Permission.findByUserGroupAndCategory(userGroup(UserGroup.ADMINISTRATORS), main));
			Assert.assertNull(Permission.findByUserGroupAndCategory(userGroup(UserGroup.ADMINISTRATORS), moderators));
			// The anonymous group has no grant in the moderators category.
			Assert.assertNull(Permission.findByUserGroupAndCategory(userGroup(UserGroup.ANONYMOUS), moderators));
			return null;
		});
	}

	@Test
	public void theConstructorDoesNotCreateTheRow() throws Throwable {
		inTransactionVoid(() -> {
			Permission unsaved = new Permission(category(2L), userGroup(UserGroup.GENERAL), Permission.reply);

			Assert.assertNull(unsaved.id);
			Assert.assertEquals("2", String.valueOf(unsaved.accessLevel));
			Assert.assertEquals("General", unsaved.group.name);
			Assert.assertEquals("Main Category", unsaved.category.name);
			// toString() works on an unsaved row, because it reads the two relations it was given.
			Assert.assertEquals("General - 2 - Main Category", unsaved.toString());
			Assert.assertEquals("5", String.valueOf(Permission.count()));
			Assert.assertEquals("General - 3 - Main Category", String.valueOf(
					Permission.findByUserGroupAndCategory(userGroup(UserGroup.GENERAL), category(2L))));
			return null;
		});
	}

	@Test
	public void createAddsASecondGrantForAPairThatAlreadyExists() throws Throwable {
		inTransactionVoid(() -> {
			Permission unsaved = new Permission(category(2L), userGroup(UserGroup.GENERAL), Permission.reply);
			unsaved.create();

			Assert.assertEquals("6", String.valueOf(unsaved.id));
			Assert.assertEquals("6", String.valueOf(Permission.count()));
			// The pair now holds two rows and find().first() answers the fixture one, so the new grant cannot
			// be resolved by group and category alone.
			Assert.assertEquals("General - 3 - Main Category", String.valueOf(
					Permission.findByUserGroupAndCategory(userGroup(UserGroup.GENERAL), category(2L))));
			Assert.assertEquals("[General - 3 - Main Category, General - 2 - Main Category]",
					names(Permission.find("group = ?1 and category = ?2",
							userGroup(UserGroup.GENERAL), category(2L)).fetch()));
			return null;
		});
	}

	@Test
	public void deleteRemovesAGrantWithoutTouchingCategoryOrGroup() throws Throwable {
		inTransactionVoid(() -> {
			Permission postMain = Permission.find("group = ?1 and category = ?2",
					userGroup(UserGroup.GENERAL), category(2L)).first();
			Assert.assertEquals("1", String.valueOf(postMain.id));

			postMain.delete();

			Assert.assertEquals("4", String.valueOf(Permission.count()));
			Assert.assertEquals("2", String.valueOf(Category.count()));
			Assert.assertEquals("4", String.valueOf(UserGroup.count()));
			Assert.assertEquals("1", String.valueOf(userGroup(UserGroup.GENERAL).getUsersCount()));
			Assert.assertNull(Permission.findByUserGroupAndCategory(userGroup(UserGroup.GENERAL), category(2L)));
			Assert.assertEquals("[Moderators - 4 - Main Category, Moderators - 3 - Moderators Category, "
					+ "General - 1 - Moderators Category, Anonymous - 1 - Main Category]", names(Permission.findAll()));
			// The category the grant pointed at is still readable, with its forums.
			Assert.assertEquals("[mainForum1, mainForum2, Home Page]", names(category(2L).getForums(1, 10)));
			return null;
		});
	}
}
