import org.junit.Assert;
import org.junit.Test;

import models.forum.Category;
import models.forum.Permission;
import models.forum.Post;
import models.forum.User;
import models.forum.UserGroup;

/**
 * Coverage of models.forum.UserGroup.
 *
 *   - The four name constants are the names the fixtures and conf/initial-data.yml insert; findByName() is
 *     how every controller and template resolves a group, and answers null for an unknown name.
 *   - toString() is the name; admin is the flag User.getCategories() short-circuits on, and only the Admin row
 *     of test/data.yml sets it.
 *   - getUsersCount() and getUsers(page, pageSize) count and page through User.find("group", this), which has
 *     no order by, so the row order is the insertion order of test/data.yml.
 *   - permissions is a @OneToMany with cascade = ALL, so a group that holds permissions and no user can be
 *     removed and its permission rows go with it. User.group_id is a plain foreign key with no cascade, so a
 *     group that still has a member cannot be removed: MySQL refuses the DELETE.
 *   - The entity has no constructor; admin/Users and the CRUD form set the fields on a blank row, where admin
 *     stays null and permissions stays null until the entity is read back.
 */
public class UserGroupModelTest extends BaseModelTest {

	@Test
	public void theNameConstantsAreTheNamesOfTheFixtureGroups() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("Anonymous", UserGroup.ANONYMOUS);
			Assert.assertEquals("General", UserGroup.GENERAL);
			Assert.assertEquals("Moderators", UserGroup.MODERATORS);
			Assert.assertEquals("Admin", UserGroup.ADMINISTRATORS);

			Assert.assertEquals("1", String.valueOf(userGroup(UserGroup.ADMINISTRATORS).id));
			Assert.assertEquals("2", String.valueOf(userGroup(UserGroup.MODERATORS).id));
			Assert.assertEquals("3", String.valueOf(userGroup(UserGroup.GENERAL).id));
			Assert.assertEquals("4", String.valueOf(userGroup(UserGroup.ANONYMOUS).id));

			Assert.assertEquals("Admin", userGroup(UserGroup.ADMINISTRATORS).toString());
			Assert.assertEquals("[Admin, Moderators, General, Anonymous]", names(UserGroup.findAll()));
			Assert.assertEquals("4", String.valueOf(UserGroup.count()));
			return null;
		});
	}

	@Test
	public void onlyTheAdministratorGroupCarriesTheAdminFlag() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("true", String.valueOf(userGroup(UserGroup.ADMINISTRATORS).admin));
			Assert.assertEquals("false", String.valueOf(userGroup(UserGroup.MODERATORS).admin));
			Assert.assertEquals("false", String.valueOf(userGroup(UserGroup.GENERAL).admin));
			Assert.assertEquals("false", String.valueOf(userGroup(UserGroup.ANONYMOUS).admin));
			// test/data.yml gives every group a description; the General one reads back as "General".
			Assert.assertEquals("General", userGroup(UserGroup.GENERAL).description);
			Assert.assertEquals("Admin", userGroup(UserGroup.ADMINISTRATORS).description);
			return null;
		});
	}

	@Test
	public void findByNameAnswersNullForAnUnknownGroupButIgnoresTheCaseOfAKnownOne() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertNull(userGroup("Nope"));
			Assert.assertNull(userGroup(""));
			Assert.assertNull(userGroup("Moderator"));
			// MariaDB's default collation (utf8mb4_general_ci) is case insensitive, so name = ?1 matches
			// "General" for "general" and "Admin" for "ADMIN". The same applies to User.findByEmail(),
			// which is why the login form lowercases nothing before the lookup.
			Assert.assertEquals("General", String.valueOf(userGroup("general")));
			Assert.assertEquals("Admin", String.valueOf(userGroup("ADMIN")));
			Assert.assertEquals("General", userGroup(UserGroup.GENERAL).toString());
			return null;
		});
	}

	@Test
	public void usersOfAGroupAreListedInInsertionOrder() throws Throwable {
		inTransactionVoid(() -> {
			Assert.assertEquals("1", String.valueOf(userGroup(UserGroup.ADMINISTRATORS).getUsersCount()));
			Assert.assertEquals("1", String.valueOf(userGroup(UserGroup.MODERATORS).getUsersCount()));
			Assert.assertEquals("1", String.valueOf(userGroup(UserGroup.GENERAL).getUsersCount()));
			Assert.assertEquals("1", String.valueOf(userGroup(UserGroup.ANONYMOUS).getUsersCount()));

			Assert.assertEquals("[Admin]", names(userGroup(UserGroup.ADMINISTRATORS).getUsers(1, 10)));
			Assert.assertEquals("[Moderator]", names(userGroup(UserGroup.MODERATORS).getUsers(1, 10)));
			Assert.assertEquals("[Bob]", names(userGroup(UserGroup.GENERAL).getUsers(1, 10)));
			Assert.assertEquals("[Anonymous]", names(userGroup(UserGroup.ANONYMOUS).getUsers(1, 10)));

			// One member per group, so page 2 of a window of one is empty.
			Assert.assertEquals("[Bob]", names(userGroup(UserGroup.GENERAL).getUsers(1, 1)));
			Assert.assertEquals("[]", names(userGroup(UserGroup.GENERAL).getUsers(2, 1)));
			return null;
		});
	}

	@Test
	public void permissionCollectionsOfTheFixtureGroups() throws Throwable {
		inTransactionVoid(() -> {
			// The Admin group has no Permission row, which is why Category.forUser() answers [] for it.
			Assert.assertEquals("0", String.valueOf(userGroup(UserGroup.ADMINISTRATORS).permissions.size()));
			Assert.assertEquals("2", String.valueOf(userGroup(UserGroup.MODERATORS).permissions.size()));
			Assert.assertEquals("2", String.valueOf(userGroup(UserGroup.GENERAL).permissions.size()));
			Assert.assertEquals("1", String.valueOf(userGroup(UserGroup.ANONYMOUS).permissions.size()));
			return null;
		});
	}

	@Test
	public void aBlankRowHasNoAdminFlagNoDescriptionAndNoPermissionCollection() throws Throwable {
		inTransactionVoid(() -> {
			UserGroup fresh = new UserGroup();
			fresh.name = "Fresh Group";
			fresh.create();

			Assert.assertEquals("5", String.valueOf(fresh.id));
			Assert.assertEquals("Fresh Group", fresh.toString());
			Assert.assertNull(fresh.admin);
			Assert.assertNull(fresh.description);
			// The collection is not initialised by the entity, unlike PlayedGame.scores.
			Assert.assertNull(fresh.permissions);
			Assert.assertEquals("0", String.valueOf(fresh.getUsersCount()));
			Assert.assertEquals("[]", names(fresh.getUsers(1, 10)));
			Assert.assertEquals("5", String.valueOf(UserGroup.count()));
			Assert.assertEquals("[Admin, Moderators, General, Anonymous, Fresh Group]", names(UserGroup.findAll()));
			return null;
		});
	}

	@Test
	public void deleteRemovesAGroupNothingPointsAt() throws Throwable {
		inTransactionVoid(() -> {
			UserGroup fresh = new UserGroup();
			fresh.name = "Fresh Group";
			fresh.create();
			fresh.delete();

			Assert.assertEquals("4", String.valueOf(UserGroup.count()));
			Assert.assertNull(userGroup("Fresh Group"));
			return null;
		});
	}

	@Test
	public void deleteOfAGroupThatHoldsPermissionsRemovesThemToo() throws Throwable {
		inTransactionVoid(() -> {
			UserGroup flagged = new UserGroup();
			flagged.name = "Flagged Group";
			flagged.admin = Boolean.TRUE;
			flagged.description = "Flagged description";
			flagged.create();
			new Permission(category(2L), flagged, Permission.view).create();

			Assert.assertEquals("6", String.valueOf(Permission.count()));
			UserGroup loaded = (UserGroup) flagged.refresh();
			Assert.assertEquals("[Flagged Group - 1 - Main Category]", names(loaded.permissions));

			// cascade = CascadeType.ALL on permissions, and no User row points at the group.
			loaded.delete();

			Assert.assertEquals("4", String.valueOf(UserGroup.count()));
			Assert.assertEquals("5", String.valueOf(Permission.count()));
			Assert.assertEquals("4", String.valueOf(User.count()));
			Assert.assertNull(userGroup("Flagged Group"));
			return null;
		});
	}

	@Test
	public void deleteOfAGroupThatHasAMemberIsRefusedByTheUserRelation() throws Throwable {
		// User.group has no cascade, so MySQL refuses the DELETE of the General group, which holds Bob.
		assertTransactionFailureContains("a foreign key constraint fails (`www`.`User`", () -> {
			UserGroup general = (UserGroup) userGroup(UserGroup.GENERAL).refresh();
			Assert.assertEquals("[General - 3 - Main Category, General - 1 - Moderators Category]",
					names(general.permissions));
			Assert.assertEquals("[Bob]", names(general.getUsers(1, 10)));
			general.delete();
			return null;
		});
	}

	@Test
	public void theGroupAndItsPermissionsSurviveTheRefusedDelete() throws Throwable {
		// The transaction of the refused delete is rolled back, so nothing of it reached the database; the
		// rows are counted in a transaction of its own.
		inTransactionVoid(() -> {
			Assert.assertEquals("4", String.valueOf(UserGroup.count()));
			Assert.assertEquals("5", String.valueOf(Permission.count()));
			Assert.assertEquals("4", String.valueOf(User.count()));
			Assert.assertEquals("6", String.valueOf(Post.count()));
			Assert.assertEquals("General", userGroup(UserGroup.GENERAL).toString());
			return null;
		});
	}
}
