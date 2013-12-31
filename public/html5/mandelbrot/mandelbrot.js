/*global window, document, resources, Float32Array*/
(function () {
	"use strict";
	var mandelbrot = {};
	mandelbrot.userInterface = function (setup) {
		var maxIterationElement = document.getElementById('maxIteration'),
			scaleElement = document.getElementById('scale'),
			dragStart = {x : null, y : null},
			dragOffset = {x : 0, y : 0},
			translate = {x : 0, y : 0},
			scale = 0.1,
			increaseIterationButton = document.getElementById('increaseIteration'),
			decreaseIterationButton = document.getElementById('decreaseIteration'),
			selectedPaletteButton = document.getElementById('selectedPalette'),
			selectionPaletteElement = document.getElementById('selectionPalette'),
			canvasElement = document.getElementById(setup.canvasId),
			modelComponent = null,
			childNodes,
			childNode,
			key,
			chooseColor = function (event) {
				var backgroundImage = event.target.style.backgroundImage,
					resourceName = event.target.getAttribute('id');
				selectionPaletteElement.style.display = 'none';
				selectedPaletteButton.style.backgroundImage = backgroundImage;
				if (modelComponent !== null) {
					modelComponent.glColorPalette(resourceName);
				}
			},
			mouseWheelHandler = function (event) {
				var wheel;
				event.stopPropagation();
				//event.preventDefault();
				if (modelComponent !== null) {
					wheel = event.detail ? -1 * event.detail : event.wheelDelta / 40;
					if (wheel > 0) {
						modelComponent.increaseScale();
					} else {
						modelComponent.decreaseScale();
					}
				}
				return false;
			};
		//Color palette selection initialisation
		selectionPaletteElement.style.display = 'none';
		//Color palette selection event handlers
		selectedPaletteButton.addEventListener('click', function () {
			if (selectionPaletteElement.style.display === 'none') {
				selectionPaletteElement.style.display = '';
			} else {
				selectionPaletteElement.style.display = 'none';
			}
		});
		childNodes = selectionPaletteElement.childNodes;
		for (key = 0; key < childNodes.length; key += 1) {
			childNode = childNodes[key];
			if (childNode.nodeType === 1 && childNode.className === 'colorPalette') {
				childNode.addEventListener('click', chooseColor);
			}
		}
		//Increase/decrease maximum iteration event handlers
		increaseIterationButton.addEventListener('click', function () {
			if (modelComponent !== null) {
				modelComponent.increaseIteration();
			}
		});
		decreaseIterationButton.addEventListener('click', function () {
			if (modelComponent !== null) {
				modelComponent.decreaseIteration();
			}
		});
		//Mouse events handlers
		canvasElement.addEventListener('DOMMouseScroll', mouseWheelHandler);
		canvasElement.addEventListener('mousewheel', mouseWheelHandler);

		canvasElement.addEventListener('mousedown', function (event) {
			event.stopPropagation();
			dragStart.x = event.clientX;
			dragStart.y = event.clientY;
			dragOffset.x = 0.0;
			dragOffset.y = 0.0;
		});
		canvasElement.addEventListener('mouseup', function (event) {
			event.stopPropagation();
			dragStart.x = null;
			dragStart.y = null;
			translate.x += dragOffset.x;
			translate.y += dragOffset.y;
			dragOffset.x = 0;
			dragOffset.y = 0;
		});
		canvasElement.addEventListener('mousemove', function (event) {
			var moveX, moveY;
			event.stopPropagation();
			if (dragStart.x !== null) {
				moveX = dragStart.x - event.clientX;
				moveY = dragStart.y - event.clientY;
				dragOffset.x = (2 / scale) * (moveX / canvasElement.width);
				dragOffset.y = (-2 / scale) * (moveY / canvasElement.height);
			}
		});
		return {
			registerModelComponent : function (value) {
				modelComponent = value;
			},
			update : function (maxIteration, modelScale) {
				scale = modelScale;
				maxIterationElement.innerHTML = Math.floor(maxIteration);
				scaleElement.innerHTML = scale >= 10 ? Math.floor(scale) : Math.round(scale * 100) / 100;
			},
			dragOffset : dragOffset,
			translate : translate
		};
	};
	/**
	 * Canvas animator, or 'the main loop',
	 * call the parameter method callback at 60fps.
	 * @param {Function} callback
	 */
	mandelbrot.animation = function (callback) {
		var running = true,
			/**
			 * 60fps timer, using the browser capability if available
			 * Source: http://paulirish.com/2011/requestanimationframe-for-smart-animating/
			 */
			requestAnimationFrame = (function () {
				// shim layer with setTimeout fallback
				if (!window.requestAnimationFrame) {
					if (window.webkitRequestAnimationFrame) {
						return window.webkitRequestAnimationFrame;
					} else if (window.mozRequestAnimationFrame) {
						return window.mozRequestAnimationFrame;
					} else if (window.oRequestAnimationFrame) {
						return window.oRequestAnimationFrame;
					} else if (window.msRequestAnimationFrame) {
						return window.msRequestAnimationFrame;
					} else {
						return function (animateCallback) {
							window.setTimeout(animateCallback, 1000 / 60);
						};
					}
				} else {
					return window.requestAnimationFrame;
				}
			}()),
			/**
			 * The loop itself, running if used to stop
			 * or continue the animation
			 */
			animate = function () {
				if (running) {
					callback();
					requestAnimationFrame(animate);
				}
			};
		return {
			/**
			 * Initialize and start the animator
			 */
			start: function () {
				running = true;
				animate();
			},
			/**
			 * stop the animator and return the average
			 * fps of the last execution
			 * @return {String} last execution's fps
			 */
			stop: function () {
				running = false;
			}
		};
	};

	mandelbrot.webGL = function (setup) {
		var canvas = document.getElementById(setup.canvasId),
			gl, //Graphical Library
			vs, //Vertex Shader
			fs, //Fragment Shader
			program, //OpenGL Program
			TEXTURE_FILTER, //OpenGL Texture Configuration
			createBuffer, //Local function
			getAttribLocation; //Local function
		if (canvas !== null) {
			try {
				gl = canvas.getContext("webgl");
			} catch(ex) {}
			if (gl === null) {
				try {
					gl = canvas.getContext("experimental-webgl");
				} catch(ex) {}
			}
		}
		if (gl === null) {
			alert("Your browser doesn't support WebGL.");
			return null;
		}
		vs = gl.createShader(gl.VERTEX_SHADER);
		fs = gl.createShader(gl.FRAGMENT_SHADER);
		program = gl.createProgram();
		TEXTURE_FILTER = gl.LINEAR;
		createBuffer = function (array) {
			var buffer = gl.createBuffer();
			gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
			gl.bufferData(gl.ARRAY_BUFFER, array, gl.STATIC_DRAW);
			return buffer;
		};
		getAttribLocation = function (attrib) {
			return gl.getAttribLocation(program, attrib);
		};

		gl.viewport(0, 0, canvas.width, canvas.height);
		gl.clearColor(0, 0, 0, 1);

		return {
			GL : gl,
			canvas : {
				width : canvas.width,
				height : canvas.height
			},
			compileVertexShader : function (sourceCode) {
				gl.shaderSource(vs, sourceCode);
				gl.compileShader(vs);
				if (!gl.getShaderParameter(vs, gl.COMPILE_STATUS)) {
					window.console.error(gl.getShaderInfoLog(vs));
				}
			},
			compileFragmentShader : function (sourceCode) {
				gl.shaderSource(fs, sourceCode);
				gl.compileShader(fs);
				if (!gl.getShaderParameter(fs, gl.COMPILE_STATUS)) {
					window.console.error(gl.getShaderInfoLog(fs));
				}
			},
			linkShaders : function () {
				gl.attachShader(program, vs);
				gl.attachShader(program, fs);
				gl.linkProgram(program);
				if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
					window.console.error(gl.getProgramInfoLog(program));
				} else {
					gl.useProgram(program);
				}
			},
			getUniformLocation : function (uniform) {
				var location = gl.getUniformLocation(program, uniform);
				if (location && location !== -1) {
					return location;
				} else {
					throw 'UniformLocation of ' + uniform + ' not found';
				}
			},
			setAttribFloatArray : function (attrib, array, itemSize) {
				var buffer = createBuffer(array),
					location = getAttribLocation(attrib);
				if (location !== -1) {
					gl.enableVertexAttribArray(location);
					gl.vertexAttribPointer(location, itemSize, gl.FLOAT, false, 0, 0);
				} else {
					throw 'AttribLocation of ' + attrib + ' not found';
				}
			},
			loadTexture2D : function (texture, image) {
				gl.bindTexture(gl.TEXTURE_2D, texture);
				gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
				gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
				gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, TEXTURE_FILTER);
				gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, TEXTURE_FILTER);
				gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.REPEAT);
			}
		};
	};

	mandelbrot.model = function (webGL, userInterface) {
		var vertices = new Float32Array([
				1, 1,
				-1, 1,
				1, -1,
				-1, -1
			]),
		    textureCoords = new Float32Array([
				2.0, 2.0,
				-2.0, 2.0,
				2.0, -2.0,
				-2.0, -2.0
			]),
			gl = webGL.GL,
			aspect = webGL.canvas.width / webGL.canvas.height,
			scale = 0.1,
			targetScale = 1,
			maxIteration = 64,
			colorTexture = null,
			uniform = {},
			glColorPalette = function (name) {
				var resource = resources.get(name);
				if (resource) {
					if (colorTexture !== null) {
						gl.deleteTexture(colorTexture);
					}
					// Load an image to use. Returns a WebGLTexture object
					colorTexture = gl.createTexture();
					webGL.loadTexture2D(colorTexture, resource);

					gl.activeTexture(gl.TEXTURE0);
					gl.bindTexture(gl.TEXTURE_2D, colorTexture);
					gl.uniform1i(uniform.colorPalette, 0);
				}
			},
			draw = function () {
				var translateX = 2 * (userInterface.translate.x + userInterface.dragOffset.x),
					translateY = 2 * (userInterface.translate.y + userInterface.dragOffset.y),
					aspectScale = scale * aspect,
					minX = (translateX * scale - 2.0) / scale,
					maxX = (translateX * scale + 2.0) / scale,
					minY = (translateY * scale - 2.0) / aspectScale,
					maxY = (translateY * scale + 2.0) / aspectScale;
				textureCoords = new Float32Array([
					maxX, maxY,
					minX, maxY,
					maxX, minY,
					minX, minY
				]);
				webGL.setAttribFloatArray("aTexturePosition", textureCoords, 2);
				gl.uniform1i(uniform.maxIteration, maxIteration);
				gl.clear(gl.COLOR_BUFFER_BIT);
				gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
			},
			animate = function () {
				scale += (targetScale - scale) / 2;
				userInterface.update(maxIteration, scale);
			},
			lastDraw = new Date(),
			that;

		webGL.compileVertexShader(resources.get('mandelbrotVertex'));
		webGL.compileFragmentShader(resources.get('mandelbrotFragment'));
		webGL.linkShaders();
		uniform = {
			colorPalette: webGL.getUniformLocation("colorPalette"),
			maxIteration: webGL.getUniformLocation("maxIteration")
		};
		webGL.setAttribFloatArray("aVertexPosition", vertices, 2);
		glColorPalette('paletteRedYellow');
		that = {
			loop : function () {
				var now = new Date();
				while (now - lastDraw > 16) {
					animate();
					lastDraw += 16;
				}
				lastDraw = now;
				draw();
			},
			increaseIteration : function () {
				maxIteration += maxIteration * 0.1;
				maxIteration = Math.min(maxIteration, 1024);
			},
			decreaseIteration : function () {
				maxIteration -= maxIteration * 0.1;
				maxIteration = Math.max(maxIteration, 64);
			},
			increaseScale : function () {
				targetScale += targetScale * 0.1;
				targetScale = Math.min(targetScale, 65535 * 2);
			},
			decreaseScale : function () {
				targetScale -= targetScale * 0.1;
				targetScale = Math.max(targetScale, 0.5);
			},
			glColorPalette : glColorPalette
		};
		userInterface.registerModelComponent(that);
		return that;
	};
	return {
		init: function (setup) {
			try {
				//IE unfriendly, for now.
				window.addEventListener('load', function () {
					var userInterface,
						webGL,
						model,
						animation;
					resources.load(function (progress) {
						if (progress === 1) {
							userInterface = mandelbrot.userInterface(setup);
							webGL = mandelbrot.webGL(setup);
							model = mandelbrot.model(webGL, userInterface);
							animation = mandelbrot.animation(model.loop);
							animation.start();
						}
					});
				}, false);
			} catch (exception) {
				if (window.console) {
					window.console.error(exception);
				}
				window.alert('Your browser doesn\'t support WebGL.');
			}
		}
	};
}()).init({canvasId: 'mycanvas'});
