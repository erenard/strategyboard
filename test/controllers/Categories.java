package controllers;

import play.mvc.Controller;

/**
 * Test-only stand-in for a Categories controller.
 *
 * app/controllers/ has no Categories class and conf/routes has no line for it, so the links inside
 * app/views/Categories/index.html and app/views/Categories/show.html -- @{Categories.show(id)},
 * @{Categories.create} and @{Categories.delete(id)} -- cannot be resolved: play.templates.GroovyTemplate's
 * ActionBridge asks ActionInvoker.getActionMethod("Categories.show") first, which raises
 * ActionNotFoundException for a controller that does not exist, and the template then fails with
 * NoRouteFoundException. These two templates are therefore dead code in the application.
 *
 * This class lives in test/, not in app/, so the application is unchanged. Play compiles the test
 * sources into the same class loader, which makes the three actions reversible through the catch-all
 * routes at the end of conf/routes ({controller}/{action}), and CategoriesViewsTest renders the two
 * templates with that. It is never called: no test requests /categories, and no route points at it.
 */
public class Categories extends Controller {

	public static void index() {
		render();
	}

	public static void show(Long id) {
		render();
	}

	public static void create(String name, String description) {
		render();
	}

	public static void delete(Long id) {
		render();
	}
}
