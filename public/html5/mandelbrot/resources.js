/*global Image, XMLHttpRequest*/
var resources = (function () {
	"use strict";
	var datas = {},
		progress = null,
		updateHandler = null,
		total = 0,
		getState = function () {
			var loaded = 0,
				key,
				data;
			for (key in datas) {
				if (datas.hasOwnProperty(key)) {
					data = datas[key];
					if ((data instanceof Image && data.complete) || (data instanceof XMLHttpRequest && data.readyState === 4)) {
						loaded += 1;
					}
				}
			}
			return loaded / total;
		},
		updateResourceState = function () {
			var newProgress = getState();
			if (updateHandler !== null && newProgress > progress) {
				progress = newProgress;
				updateHandler(progress);
			}
		},
		loadImage = function (name, url) {
			var image = new Image();
			total += 1;
			datas[name] = image;
			image.onload = updateResourceState;
			progress = 0.0;
			image.src = url;
		},
		loadShader = function (name, url) {
			var shader = new XMLHttpRequest();
			total += 1;
			datas[name] = shader;
			shader.onreadystatechange = updateResourceState;
			shader.open("GET", url, true);
			progress = 0;
			shader.send();
		};
	return {
		get : function (name) {
			var data = datas[name];
			if (data !== null && data instanceof XMLHttpRequest && data.readyState === 4) {
				return data.responseText;
			} else {
				return data;
			}
		},
		load : function (updateHandlerFct) {
			loadImage('paletteBlueYellow', '/public/html5/mandelbrot/paletteBlueYellow.png');
			loadImage('paletteFrequency', '/public/html5/mandelbrot/paletteFrequency.png');
			loadImage('paletteHue', '/public/html5/mandelbrot/paletteHue.png');
			loadImage('paletteRedGreen', '/public/html5/mandelbrot/paletteRedGreen.png');
			loadImage('paletteRedYellow', '/public/html5/mandelbrot/paletteRedYellow.png');
			loadImage('paletteWavelength', '/public/html5/mandelbrot/paletteWavelength.png');
			loadShader('mandelbrotVertex', '/public/html5/mandelbrot/mandelbrotVertex.c');
			loadShader('mandelbrotFragment', '/public/html5/mandelbrot/mandelbrotFragment.c');
			updateHandler = updateHandlerFct;
		}
	};
}());
