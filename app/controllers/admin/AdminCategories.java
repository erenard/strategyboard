package controllers.admin;

import controllers.CRUD;
import controllers.Check;
import controllers.Profile;
import controllers.Secure;
import models.forum.Category;
import play.mvc.*;

@With(Secure.class)
@Check(Profile.ADMINISTRATOR)
@CRUD.For(Category.class)
public class AdminCategories extends CRUD {
    
}