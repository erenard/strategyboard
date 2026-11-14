import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.*;

import play.mvc.Scope;

import models.forum.Category;
import models.forum.Forum;

/** Rows and the connected flag of app/views/Categories/*.html: see TemplateStandIns.java. */

/**
 * Coverage of app/views/Categories/index.html and app/views/Categories/show.html.
 *
 * Neither template can be reached by a request: conf/routes has no line mentioning Categories, and the
 * catch-all route resolves {controller}.{action} only for a controller that exists, while
 * ActionInvoker.getActionMethod("Categories.show") raises ActionNotFoundException for a missing one --
 * the template then fails with NoRouteFoundException. So both are rendered through
 * play.templates.TemplateLoader (BaseForumViewTest.renderView), and @{Categories.show(id)},
 * @{Categories.create} and @{Categories.delete(id)} become reversible through the catch-all routes
 * because test/controllers/Categories.java declares those three actions in the same class loader.
 *
 * The bindings the templates need are described in TemplateStandIns.java: index.html reads
 * category.description, which models.forum.Category does not have, and both templates end with
 * #{if connected?.admin}, a name nothing ever binds. show.html reads real Category objects, whose
 * postsCount, topicsCount, getForums(page, pageSize) and getLastPost() are real queries.
 *
 * index.html needs forumsCount, categoriesCount and categories; show.html needs category, pageSize and
 * the page #{pagination} and category.getForums(page ?: 1, pageSize) read. One case adds a category
 * with one forum that has no topic and no post, which is what makes the false branches of
 * #{if category.lastPost} and #{if forum.lastPost} appear next to rows that do have a last post.
 */
public class CategoriesViewsTest extends BaseForumViewTest {

	/** Adds "Empty Category" with one forum that has no topic and no post, and returns its id. */
	private static long seedEmptyCategory() throws Throwable {
		return inTransaction(() -> {
			Category empty = new Category("Empty Category", 6);
			new Forum(empty, "emptyForum", "Empty forum description", 6);
			return empty.id;
		});
	}

	private static Category mainCategory() throws Throwable {
		return inTransaction(() -> Category.findById(2L));
	}

	/**
	 * The binding of Categories/index.html: the counts are the real ones, the rows are CategoryRow
	 * objects read from the real categories inside a transaction.
	 */
	private static Map<String, Object> indexArgs(String username, TemplateStandIns.Connected connected,
			String keptDescription) throws Throwable {
		Map<String, Object> args = viewArgs(username, "/categories");
		args.put("forumsCount", inTransaction(() -> Forum.count()));
		args.put("categoriesCount", inTransaction(() -> Category.count()));
		args.put("categories", rows());
		if (connected != null) {
			args.put("connected", connected);
		}
		if (keptDescription != null) {
			((Scope.Flash) args.get("flash")).put("description", keptDescription);
		}
		return args;
	}

	private static List<Object> rows() throws Throwable {
		final List<Object> rows = new ArrayList<Object>();
		inTransactionVoid(() -> {
			for (play.db.jpa.JPABase model : Category.findAll()) {
				Category category = (Category) model;
				rows.add(TemplateStandIns.rowOf(category, category.name + " description"));
			}
			return null;
		});
		return rows;
	}

	private static Map<String, Object> showArgs(String username, Integer page, int pageSize,
			TemplateStandIns.Connected connected) throws Throwable {
		Map<String, Object> args = viewArgs(username, "/categories/2");
		args.put("category", mainCategory());
		args.put("pageSize", pageSize);
		if (page != null) {
			args.put("page", page);
		}
		if (connected != null) {
			args.put("connected", connected);
		}
		return args;
	}

	// ~~~~~~~~~~~~ Categories/index.html

	@Test
	public void indexListsEveryCategoryWithItsLastPostOrNoPost() throws Throwable {
		long emptyId = seedEmptyCategory();

		String body = renderView("Categories/index.html", indexArgs("anonymous@domain.com", null, null));

		// pluralize on both counts of the subtitle: 5 fixture forums + the one just created
		assertHeader(body, "Categories", "6 forums in 3 categorys");
		assertContains("<table id=\"categories-list\">", body);
		assertContains("<thead> <tr> <th class=\"main\">Category</th> <th class=\"infos\">Last post</th> </tr> </thead>", body);
		Assert.assertEquals(3, tableRows(body).size());

		// @{Categories.show(id)} goes through the catch-all route, so the id lands in the query string
		assertContains("<a href=\"/categories/show?id=2\">Main Category</a>", body);
		assertContains("<p class=\"light\"> 3 forums </p> Main Category description </td>", body);
		// mainCategory has posts, so its row takes the #{if category.lastPost} branch
		assertContains("<br /> by <strong>Admin</strong> (<a href=\"/forums/2/topics/5?page=1\">view</a>) </td>", body);

		// the new category has one forum and no post at all -> the #{else} branch
		assertContains("<a href=\"/categories/show?id=" + emptyId + "\">Empty Category</a>", body);
		assertContains("<p class=\"light\"> 1 forum </p> Empty Category description </td>", body);
		assertContains("<td class=\"infos\"> No post </td>", body);
		// Moderators Category has forums but no post, so it also writes No post
		assertContains("<a href=\"/categories/show?id=1\">Moderators Category</a> <p class=\"light\"> 2 forums </p> Moderators Category description </td> <td class=\"infos\"> No post </td>", body);

		// connected is not bound -> connected?.admin is false -> no admin block
		assertNotContains("youreadmin", body);
		assertNotContains("Create a new category", body);
	}

	@Test
	public void indexShowsTheAdministratorFormWithNoFieldErrorAndAnEmptyDescription() throws Throwable {
		String body = renderView("Categories/index.html",
				indexArgs("anonymous@domain.com", TemplateStandIns.admin(), null));

		assertContains("<div id=\"youreadmin\"> Administrator options </div>", body);
		assertContains("<h2>Create a new category</h2>", body);
		// @{Categories.create} reverses through the catch-all route
		assertContains("<form action=\"/categories/create\" method=\"POST\">", body);
		// #{error 'name'} with no validation error -> an empty span; flash.description is absent too
		assertContains("<input type=\"text\" name=\"name\" size=\"30\" id=\"name\" /> <span class=\"error\"></span>", body);
		assertContains("<label for=\"description\">Set a description</label> <textarea name=\"description\" rows=\"6\" id=\"description\"></textarea>", body);
		assertContains("<input type=\"submit\" value=\"Add this category\" id=\"addCategory\" />", body);
	}

	@Test
	public void indexKeepsTheDescriptionAndNamesTheErrorOfTheNameField() throws Throwable {
		Map<String, Object> args = indexArgs("anonymous@domain.com", TemplateStandIns.admin(),
				"Kept description");

		String body = renderViewWithError("Categories/index.html", args, "name", "Name is required");

		assertContains("<span class=\"error\">Name is required</span>", body);
		assertContains("<textarea name=\"description\" rows=\"6\" id=\"description\">Kept description</textarea>", body);
	}

	// ~~~~~~~~~~~~ Categories/show.html

	@Test
	public void showListsTheForumsOfACategoryWithoutPaginationOrAdminOptions() throws Throwable {
		String body = renderView("Categories/show.html", showArgs("anonymous@domain.com", null, 10, null));

		// pluralize of the posts and topics counts of mainCategory
		assertHeader(body, "Main Category", "6 posts in 5 topics");
		assertContains("<ol class=\"breadcrumb\"> <li><a href=\"/forums\">Forums</a></li> <li class=\"active\">Main Category</li> </ol>", body);
		assertContains("<table id=\"topics-list\">", body);
		// 3 forums with pageSize 10 -> one page -> #{pagination} writes no block
		Assert.assertNull(paginationBlock(body));
		Assert.assertEquals(3, tableRows(body).size());

		// mainForum1 has posts, so #{if forum.lastPost} is true in its row
		assertContains("<td class=\"main\"> <a href=\"/forums/1?page=1\">mainForum1</a> </td> <td class=\"infos count\"> 3 </td> <td class=\"infos count\"> 4 </td>", body);
		assertContains("by <strong>Moderator</strong> (<a href=\"/forums/1/topics/2?page=1\">view</a>) </td>", body);
		// mainForum2 has no topic and no post -> the cell keeps nothing but its whitespace
		assertContains("<td class=\"main\"> <a href=\"/forums/2?page=1\">mainForum2</a> </td> <td class=\"infos count\"> 0 </td> <td class=\"infos count\"> 0 </td> <td class=\"infos\"> </td>", body);

		// connected is not bound -> no admin block
		assertNotContains("youreadmin", body);
		assertNotContains("Delete this category", body);
	}

	@Test
	public void showPaginatesAndTakesTheSecondPageWhenTheCallerGivesOne() throws Throwable {
		// pageSize 1 -> category.forumsCount == 3 -> the pagination block appears
		String firstPage = renderView("Categories/show.html", showArgs("anonymous@domain.com", null, 1, null));
		// page is absent -> page ?: 1 -> the first page is the active one
		assertContains("<ul class=\"pagination\"> <li class=\"active\"><a href=\"#\">1</a></li> <li><a href=\"/categories/2?page=2\">2</a></li> <li><a href=\"/categories/2?page=3\">3</a></li> </ul>",
				paginationBlock(firstPage));
		Assert.assertEquals(1, tableRows(firstPage).size());
		assertContains("mainForum1", firstPage);
		assertNotContains("mainForum2", firstPage);

		// page 2 -> page ?: 1 resolves to the caller's page -> getForums(2, 1) lists mainForum2
		String secondPage = renderView("Categories/show.html", showArgs("anonymous@domain.com", 2, 1, null));
		assertContains("<ul class=\"pagination\"> <li><a href=\"/categories/2?page=1\">1</a></li> <li class=\"active\"><a href=\"#\">2</a></li> <li><a href=\"/categories/2?page=3\">3</a></li> </ul>",
				paginationBlock(secondPage));
		Assert.assertEquals(1, tableRows(secondPage).size());
		assertContains("mainForum2", secondPage);
		assertNotContains("mainForum1", secondPage);
	}

	@Test
	public void showRendersAnEmptyForumCellWithoutALastPost() throws Throwable {
		long emptyId = seedEmptyCategory();

		Map<String, Object> args = viewArgs("anonymous@domain.com", "/categories/" + emptyId);
		args.put("category", inTransaction(() -> Category.findById(emptyId)));
		args.put("pageSize", 10);

		String body = renderView("Categories/show.html", args);

		assertHeader(body, "Empty Category", "0 posts in 0 topics");
		assertContains("<ol class=\"breadcrumb\"> <li><a href=\"/forums\">Forums</a></li> <li class=\"active\">Empty Category</li> </ol>", body);
		Assert.assertNull(paginationBlock(body));
		Assert.assertEquals(1, tableRows(body).size());
		assertMatches("<td class=\"main\"> <a href=\"/forums/[^\"]+\">emptyForum</a> </td> <td class=\"infos count\"> 0 </td> <td class=\"infos count\"> 0 </td> <td class=\"infos\"> </td>", body);
	}

	@Test
	public void showOffersTheDeleteFormToAnAdministrator() throws Throwable {
		String body = renderView("Categories/show.html", showArgs("bob@domain.com", null, 10, TemplateStandIns.admin()));

		assertContains("<div id=\"youreadmin\"> Administrator options </div>", body);
		assertContains("<h2>Delete this category</h2>", body);
		// @{Categories.delete(category.id)} reverses through the catch-all route with the id as a query
		assertContains("<form action=\"/categories/delete?id=2\" method=\"POST\"> <input type=\"submit\" value=\"Delete it !\" id=\"deleteIt\" /> </form>", body);
	}

	@Test
	public void showWritesNoAdminBlockWhenConnectedIsNotAnAdministrator() throws Throwable {
		String body = renderView("Categories/show.html", showArgs("bob@domain.com", null, 10, TemplateStandIns.notAdmin()));

		assertNotContains("youreadmin", body);
		assertNotContains("Delete this category", body);
	}
}
