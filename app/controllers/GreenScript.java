package controllers;

import play.mvc.With;
import controllers.greenscript.Configurator;

@With({Secure.class, Configurator.class})
@Check(Profile.ADMINISTRATOR)
public class GreenScript extends Configurator {}