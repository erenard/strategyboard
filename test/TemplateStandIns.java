import java.util.ArrayList;
import java.util.List;

import play.db.Model;

import controllers.CRUD;

import models.forum.Category;
import models.forum.Forum;
import models.forum.Post;

/**
 * The objects three app/views templates ask for but nothing in the application ever gives them.
 *
 * These classes live in test/, so the application stays untouched; they only ever appear in the
 * renderArgs of a direct render (BaseForumViewTest.renderView), never in a request.
 *
 *   - Connected: app/views/Categories/index.html and app/views/Categories/show.html end with
 *     #{if connected?.admin}. Like errors/403.html's #{if connected}, "connected" is a name the
 *     framework never binds (Profile.Security.connected() is a Security hook, not a renderArg), and
 *     models.forum.User has no "admin" field, so a User bound under that name raises
 *     "No such property: admin for class: models.forum.User". An object with an admin flag is the
 *     only way the true branch appears.
 *   - CategoryRow: app/views/Categories/index.html writes ${category.description}, and
 *     models.forum.Category has only name, displayOrder and forums, so no Category in the database
 *     can satisfy the template. The row carries the names the template reads, taken from a real
 *     Category so the counts and the last post stay the real ones.
 *   - CategoryObjectType: app/views/admin/AdminCategories/list.html asks for
 *     #{crud.table fields:['name', 'description', 'forums']}, and modules/crud/app/views/tags/crud/
 *     table.html reads _caller.type.getField(field).type for every field but the first, which is a
 *     NullPointerException on the missing "description". The subclass hands out a text descriptor for
 *     a field the entity does not have.
 */
public class TemplateStandIns {

	/** What #{if connected?.admin} needs: a name that answers the admin property. */
	public static class Connected {
		public boolean admin;

		public String toString() {
			return admin ? "Admin" : "Guest";
		}
	}

	public static Connected admin() {
		Connected connected = new Connected();
		connected.admin = true;
		return connected;
	}

	public static Connected notAdmin() {
		Connected connected = new Connected();
		connected.admin = false;
		return connected;
	}

	/** A row of app/views/Categories/index.html, read from a real Category inside a JPA transaction. */
	public static class CategoryRow {
		public Long id;
		public String name;
		public String description;
		public long forumsCount;
		public long topicsCount;
		public long postsCount;
		public List<Forum> forums = new ArrayList<Forum>();
		public Post lastPost;

		public Object _key() {
			return id;
		}

		public String toString() {
			return name;
		}
	}

	/** Must be called inside a JPA transaction: the counts and the last post are queries. */
	public static CategoryRow rowOf(Category category, String description) {
		CategoryRow row = new CategoryRow();
		row.id = category.id;
		row.name = category.name;
		row.description = description;
		row.forumsCount = category.getForumsCount();
		row.topicsCount = category.getTopicsCount();
		row.postsCount = category.getPostsCount();
		row.forums = category.forums;
		row.lastPost = category.getLastPost();
		return row;
	}

	/** A row of app/views/admin/AdminCategories/list.html, which reads name and description only. */
	public static CategoryRow crudRow(Long id, String name, String description) {
		CategoryRow row = new CategoryRow();
		row.id = id;
		row.name = name;
		row.description = description;
		return row;
	}

	/** The CRUD ObjectType of controllers.admin.AdminCategories, with a descriptor for "description". */
	public static class CategoryObjectType extends CRUD.ObjectType {
		public CategoryObjectType(Class<? extends Model> entityClass) {
			super(entityClass);
		}

		@Override
		public ObjectField getField(String name) {
			ObjectField field = super.getField(name);
			if (field != null) {
				return field;
			}
			// Any descriptor of the entity is enough: the table tag reads type and name, nothing else.
			ObjectField synthetic = new ObjectField(
					Model.Manager.factoryFor(entityClass).listProperties().get(0));
			synthetic.name = name;
			synthetic.type = "text";
			return synthetic;
		}
	}
}
