package controllers.admin;

import controllers.CRUD;
import controllers.Check;
import controllers.Profile;
import controllers.Secure;
import models.forum.Permission;
import play.mvc.*;

@With(Secure.class)
@Check(Profile.ADMINISTRATOR)
@CRUD.For(Permission.class)
public class AdminPermissions extends CRUD {
    
}