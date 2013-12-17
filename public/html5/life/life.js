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
			sprites = {}, //Sprites storage
			sizeX = grid.size.x,
			sizeY = grid.size.y,
			cells = grid.getCells(),
			/**
			 * prepare a cell's sprite and store it for a later use
			 * @param {Number} size : sprite's pixel size
			 * @param {String} color : fill style
			 */
			sprite = function (size, color) {
				var sprite = window.document.createElement('canvas'),
					context;
				sprite.setAttribute('width', size + 'px');
				sprite.setAttribute('height', size + 'px');
				context = sprite.getContext('2d');
				context.fillStyle = color;
				context.beginPath();
				context.moveTo(0.1, 0.1);
				context.lineTo(size * 0.9, 0.1);
				context.lineTo(size * 0.9, size * 0.9);
				context.lineTo(0.1, size * 0.9);
				context.closePath();
				context.fill();
				return sprite;
			},
			/** draws the game board with each cells */
			render = function () {
				var x, y, collumn, cell, color;
				ctx.clearRect(0, 0, canvas.width, canvas.height);
				for (x = 0; x < sizeX; x = x + 1) {
					collumn = cells[x];
					for (y = 0; y < sizeY; y = y + 1) {
						cell = collumn[y];
						if (cell.state === 1) {
							color = cell.color();
							if (sprites[color] === undefined) {
								sprites[color] = sprite(size, color);
							}
							ctx.drawImage(sprites[color], x * size, y * size);
						}
					}
				}
			};
		/* This function will wrap the whole process of updating the game and drawing it */
		return function () {
			var now = new Date(),
				time = now - lastRender;
			if (time > period) {
				grid.update();
				render();
				if (fpsElement !== null) {
					fpsElement.innerHTML = Math.floor(1000 / time);
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
				var xm1 = cells[sizeX - 1], //column x minus 1
					xs0 = cells[0], //column x
					xp1 = cells[1], //column x plus 1
					ym1, //index y minus 1
					yp1, //index y plus 1
					count, //neighboring cells count
					x, //index x
					y; //index y
				/* Phase 1, plant new cells and mark cells for death where appropriate */
				for (x = 0; x < sizeX; x = x + 1) {
					for (y = 0; y < sizeY; y = y + 1) {
						ym1 = y > 0 ? y - 1 : sizeY - 1;
						yp1 = y < (sizeY - 1) ? y + 1 : 0;
						count = xm1[ym1].state + xs0[ym1].state + xp1[ym1].state + xm1[y].state + xp1[y].state + xm1[yp1].state + xs0[yp1].state + xp1[yp1].state;
						if (xs0[y].state === 1 && !survival[count]) {
							xs0[y].nextState = 0;
						} else if (xs0[y].state === 0 && birth[count]) {
							xs0[y].nextState = 1;
						}
					}
					xm1 = xs0;
					xs0 = xp1;
					xp1 = cells[x + 2 < sizeX ? x + 2 : 0];
				}
				/* Phase 2, update cells */
				for (x = 0; x < sizeX; x = x + 1) {
					for (y = 0; y < sizeY; y = y + 1) {
						cells[x][y].compute();
					}
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
						y;
					for (x = 0; x < sizeX; x = x + 1) {
						for (y = 0; y < sizeY; y = y + 1) {
							if (Math.random() + ratio > 1) {
								cells[x][y].nextState = 1;
								cells[x][y].state = 1;
							}
						}
					}
				},
				/**
				 * Clear the game board
				 */
				clear: function () {
					var x,
						y;
					for (x = 0; x < sizeX; x = x + 1) {
						for (y = 0; y < sizeY; y = y + 1) {
							cells[x][y].nextState = 0;
							cells[x][y].state = 0;
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
	 * @param {Number} initialState Initial state of the cell, 0 or 1.
	 */
	life.Cell = function (initialState) {
		this.state = 0;
		this.nextState = 0;
		this.age = -1;
	};
	/**
	 * Make the cell live one turn
	 * Eventually going to the state 0 (dead)
	 * Agging if still alive
	 * @return {boolean} should this cell live another day ?
	 */
	life.Cell.prototype.compute = function () {
		this.state = this.nextState;
		this.age = this.state === 1 ? this.age + 1 : -1;
	};
	/**
	 * Age based color of the cell
	 * @returns {String} rgba string of the cell's color
	 */
	life.Cell.prototype.color = function () {
		return this.age > 5 ? "rgba(127, 255, 127, 1)" : "rgba(0, 127, 0, 1)";
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
					renderer = life.renderer(canvas, radius, grid, 1000 / 25),
					animation = life.animation(renderer, userInterface);
				grid.random(0.10);
				animation.start();
			}, false);
		}
	};
}()).load({lifeCellSize: 4});
