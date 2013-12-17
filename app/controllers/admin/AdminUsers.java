package controllers.admin;

import controllers.CRUD;
import controllers.Check;
import controllers.Profile;
import controllers.Secure;
import models.forum.User;
import play.mvc.*;

@With(Secure.class)
@Check(Profile.ADMINISTRATOR)
@CRUD.For(User.class)
public class AdminUsers extends CRUD {
    
}