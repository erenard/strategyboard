package controllers.admin;

import controllers.CRUD;
import controllers.Check;
import controllers.Profile;
import controllers.Secure;
import models.forum.UserGroup;
import play.mvc.*;

@With(Secure.class)
@Check(Profile.ADMINISTRATOR)
@CRUD.For(UserGroup.class)
public class AdminUserGroups extends CRUD {
    
}