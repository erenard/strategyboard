/*global window, document*/
(function () {
	"use strict";
	var life = {};
	life.userInterface = function (controls) {
		var birthElements = [
				document.getElementById("b0"),
				document.getElementById("b1"),
				document.getElementById("b2"),
				document.getElementById("b3"),
				document.getElementById("b4"),
				document.getElementById("b5"),
				document.getElementById("b6"),
				document.getElementById("b7"),
				document.getElementById("b8")
			],
			survivalElements = [
				document.getElementById("s0"),
				document.getElementById("s1"),
				document.getElementById("s2"),
				document.getElementById("s3"),
				document.getElementById("s4"),
				document.getElementById("s5"),
				document.getElementById("s6"),
				document.getElementById("s7"),
				document.getElementById("s8")
			],
			presetsElement = document.getElementById('presets'),
			stopButtonElement = document.getElementById('stop'),
			startButtonElement = document.getElementById('start'),
			randomButtonElement = document.getElementById('random'),
			clearButtonElement = document.getElementById('clear'),
			ratioElement = document.getElementById('ratio'),
			rules = {
				b: [false, false, false, false, false, false, false, false, false],
				s: [false, false, false, false, false, false, false, false, false]
			},
			index = 0,
			gridComponent = null,
			animationComponent = null,
			updateRules = function () {
				var index;
				for (index = 0; index < 9; index += 1) {
					rules.b[index] = birthElements[index].checked;
					rules.s[index] = survivalElements[index].checked;
				}
				presetsElement.value = 'custom';
			},
			loadPreset = function () {
				var preset = presetsElement.value,
					start = preset.indexOf('b'),
					stop = preset.indexOf('s'),
					length = preset.length,
					index;
				if (length !== 0 && preset !== 'custom' && start !== -1 && stop !== -1 && start < stop) {
					for (index = 0; index < 9; index += 1) {
						rules.b[index] = false;
						rules.s[index] = false;
						birthElements[index].checked = false;
						survivalElements[index].checked = false;
					}
					for (index = start + 1; index < stop; index += 1) {
						birthElements[preset.charAt(index)].checked = true;
						rules.b[preset.charAt(index)] = true;
					}
					for (index = stop + 1; index < length; index += 1) {
						survivalElements[preset.charAt(index)].checked = true;
						rules.s[preset.charAt(index)] = true;
					}
				}
			};
		presetsElement.addEventListener('change', loadPreset);
		stopButtonElement.addEventListener('click', function () {
			if (animationComponent !== null) {
				animationComponent.stop();
				stopButtonElement.style.display = 'none';
				if (startButtonElement !== null) {
					startButtonElement.style.display = '';
				}
			}
		});
		startButtonElement.addEventListener('click', function () {
			if (animationComponent !== null) {
				animationComponent.start();
				startButtonElement.style.display = 'none';
				if (stopButtonElement !== null) {
					stopButtonElement.style.display = '';
				}
			}
		});
		randomButtonElement.addEventListener('click', function () {
			var ratioValue;
			if (gridComponent !== null && ratioElement !== null) {
				ratioValue = ratioElement.value;
				if (isNaN(ratioValue) || ratioValue < 0 || ratioValue > 100) {
					ratioValue = 30;
					ratioElement.value = ratioValue;
				}
				gridComponent.random(ratioValue / 100);
			}
		});
		clearButtonElement.addEventListener('click', function () {
			if (gridComponent !== null) {
				gridComponent.clear();
			}
		});
		presetsElement.addEventListener('change', loadPreset);
		for (index = 0; index < 9; index += 1) {
			birthElements[index].addEventListener('change', updateRules);
			survivalElements[index].addEventListener('change', updateRules);
		}
		loadPreset();
		return {
			rules : rules,
			registerGrid : function (grid) {
				gridComponent = grid;
			},
			registerAnimation : function (animation) {
				animationComponent = animation;
			}
		};
	};
	/**
	 * Canvas animator, or 'the main loop',
	 * call the parameter method callback at 60fps.
	 * @param {Function} callback
	 * @param {life.userInterface} userInterface : the page's gui
	 */
	life.animation = function (callback, userInterface) {
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
			},
			that = {
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
		userInterface.registerAnimation(that);
		return that;
	};
	/**
	 * Do the drawings on the canvas
	 * @param {Element} canvas
	 * @param {Number} size
	 * @param {life.grid} grid
	 * @param {Number} period : time in millisecond between two updates
	 */
	life.renderer = function (canvas, size, grid, period) {
		var ctx = canvas.getContext('2d'),
			lastRender = new Date(),
			fpsElement = document.getElementById('fps'),
			sizeX = grid.size.x,
			sizeY = grid.size.y,
			cells = grid.getCells(),
			/**
			 * prepare a cell's sprite and store it for a later use
			 * @param {String} color : fill style
			 */
			sprite = function (color) {
				var sprite = window.document.createElement('canvas'),
					context;
				sprite.setAttribute('width', (size - 1) + 'px');
				sprite.setAttribute('height', (size - 1) + 'px');
				context = sprite.getContext('2d');
				context.fillStyle = color;
				context.rect(0, 0, size, size);
				context.fill();
				return sprite;
			},
			yngCellSprite = sprite("rgba(0, 127, 0, 1)"),
			oldCellSprite = sprite("rgba(127, 255, 127, 1)"),
			dedCellSprite = sprite("rgba(0, 0, 0, 1)"),
			/** draws the game board with each cells */
			render = function () {
				var x, y, collumn, cell;
				//ctx.clearRect(0, 0, canvas.width, canvas.height);
				x = sizeX;
				while (x--) {
					collumn = cells[x];
					y = sizeY;
					while (y--) {
						cell = collumn[y];
						if (cell.flip) {
							cell.flip = false;
							if (cell.state === 0) {
								ctx.drawImage(yngCellSprite, x * size, y * size);
								cell.age = 0;
								cell.state = 1;
							} else {
								ctx.drawImage(dedCellSprite, x * size, y * size);
								cell.age = -1;
								cell.state = 0;
							}
						} else {
							cell.age += cell.state;
							if (cell.age === 5) {
								ctx.drawImage(oldCellSprite, x * size, y * size);
							}
						}
					}
				}
			},
			times = new Array(),
			totalTime = 0;
			/// disable image smoothing for sake of speed
			ctx.webkitImageSmoothingEnabled = false;
			ctx.mozImageSmoothingEnabled = false;
			ctx.msImageSmoothingEnabled = false;
			ctx.oImageSmoothingEnabled = false;
			ctx.imageSmoothingEnabled = false;  ///future...
		/* This function will wrap the whole process of updating the game and drawing it */
		return function () {
			var now = new Date(),
				time = now - lastRender;
			if (time > period) {
				grid.update();
				render();
				if (fpsElement !== null) {
					totalTime += time;
					times.push(time);
					if (times.length > 60) {
						time = times.shift();
						totalTime -= time;
					}
					fpsElement.innerHTML = (times.length * 1000 / totalTime) | 0;
				}
				lastRender = now;
			}
		};
	};
	/**
	 * grid: implements the game algorithm
	 * @param {Number} sizeX : game board's width
	 * @param {Number} sizeY : game board's height
	 * @param {life.userInterface} userInterface : the page's gui
	 */
	life.grid = function (sizeX, sizeY, userInterface) {
		var cells = [],
			birth = userInterface.rules.b,
			survival = userInterface.rules.s,
			/** Game of life algorithm */
			compute = function () {
				var xm1 = cells[sizeX - 2], //column x minus 1
					xs0 = cells[sizeX - 1], //column x
					xp1 = cells[0], //column x plus 1
					ym1, //index y minus 1
					yp1, //index y plus 1
					cell, //current cell
					isAlive, //current cell state
					count, //neighboring cells count
					x, //index x
					y; //index y
				/* Phase 1, plant new cells and mark cells for death where appropriate */
				x = sizeX;
				while (x--) {
					y = sizeY;
					while (y--) {
						ym1 = y > 0 ? y - 1 : sizeY - 1;
						yp1 = y < (sizeY - 1) ? y + 1 : 0;
						count = xm1[ym1].state + xs0[ym1].state + xp1[ym1].state + xm1[y].state + xp1[y].state + xm1[yp1].state + xs0[yp1].state + xp1[yp1].state;
						cell = xs0[y];
						isAlive = cell.state === 1;
						cell.flip |= (isAlive && !survival[count]) || (!isAlive && birth[count]);
					}
					xp1 = xs0;
					xs0 = xm1;
					xm1 = cells[x - 1 > 0 ? x - 2 : sizeX - 1];
				}
			},
			x,
			y,
			that = {
				/**
				 * Update the game board
				 */
				update: compute,
				/**
				 * Fill the game board with cells
				 * @param {Number} ratio : filling ratio from 0.0 to 1.0
				 */
				random: function (ratio) {
					var x,
						y,
						cell;
					for (x = 0; x < sizeX; x = x + 1) {
						for (y = 0; y < sizeY; y = y + 1) {
							if (Math.random() + ratio > 1) {
								cell = cells[x][y];
								cell.flip |= cell.state !== 1;
							}
						}
					}
				},
				/**
				 * Clear the game board
				 */
				clear: function () {
					var x,
						y,
						cell;
					for (x = 0; x < sizeX; x = x + 1) {
						for (y = 0; y < sizeY; y = y + 1) {
							cell = cells[x][y];
							cell.state = 1;
							cell.flip = true;
						}
					}
				},
				/**
				 * Expose the game board
				 */
				getCells: function () {
					return cells;
				},
				/**
				 * Read-only access to the game board's bounds
				 */
				size: {
					x: sizeX,
					y: sizeY
				}
			};
		/* game board initialisation */
		for (x = 0; x < sizeX; x = x + 1) {
			cells[x] = [];
			for (y = 0; y < sizeY; y = y + 1) {
				cells[x][y] = new life.Cell();
			}
		}
		userInterface.registerGrid(that);
		return that;
	};
	/**
	 * Cell
	 */
	life.Cell = function () {
		this.state = 0;
		this.flip = false;
		this.age = -1;
	};
	return {
		// ---- launch script -----
		load: function (setup) {
			window.addEventListener('load', function () {
				//Cell radius
				var radius = setup.lifeCellSize,
					canvas = document.getElementById('viewport'),
					userInterface = life.userInterface(),
					grid = life.grid(Math.floor(canvas.width / radius), Math.floor(canvas.height / radius), userInterface),
					renderer = life.renderer(canvas, radius, grid, 1000 / 100),
					animation = life.animation(renderer, userInterface);
				grid.random(0.30);
				animation.start();
			}, false);
		}
	};
}()).load({lifeCellSize: 4});
