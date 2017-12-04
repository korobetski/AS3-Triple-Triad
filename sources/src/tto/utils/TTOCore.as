package tto.utils {
	import com.adobe.utils.ArrayUtil;
	import flash.utils.setTimeout;
	import tto.anims.ComboAnim;
	import tto.anims.PlusAnim;
	import tto.anims.SameAnim;
	import tto.datas.tripleTriadRules;
	import tto.display.Card;
	import tto.display.Tile;
	import tto.screens.BaseMatchScreen;
	/**
	 * ...
	 * @author Mao
	 */
	public class TTOCore {
		private var _RULES:Object;
		private var _SCREEN:BaseMatchScreen;
		
		public function TTOCore() {
			
		}
		
		public function applyRules(tile:Tile, color:String, checking:Boolean = false):uint {
			//trace('apply_RULES : ' + tile.id, checking);
			tile.color = color;
			
			if (_RULES.FALLEN_ACE) {
				tile.topPow = (tile.card.topPow == 10) ? 0 : tile.card.topPow;
				tile.rightPow = (tile.card.rightPow == 10) ? 0 : tile.card.rightPow;
				tile.bottomPow = (tile.card.bottomPow == 10) ? 0 : tile.card.bottomPow;
				tile.leftPow = (tile.card.leftPow == 10) ? 0 : tile.card.leftPow;
			} else {
				tile.topPow = tile.card.topPow;
				tile.rightPow = tile.card.rightPow;
				tile.bottomPow = tile.card.bottomPow;
				tile.leftPow = tile.card.leftPow;
			}
			if (_RULES.TYPE_RULE == tripleTriadRules.RULE_ASCENSION || _RULES.TYPE_RULE == tripleTriadRules.RULE_DESCENSION) {
				if (tile.card) {
					tile.topPow = tools.madmax(int(tile.topPow) + tile.card.modifier);
					tile.rightPow = tools.madmax(int(tile.rightPow) + tile.card.modifier);
					tile.bottomPow = tools.madmax(int(tile.bottomPow) + tile.card.modifier);
					tile.leftPow = tools.madmax(int(tile.leftPow) + tile.card.modifier);
				}
			}
			
			if (_RULES.TYPE_RULE == tripleTriadRules.RULE_ELEMENTAL) {
				if (tile.card.data.type == tile.element) tile.card.modifier = 1;
				else if (tile.element !== "none" && tile.card.type !== tile.element) tile.card.modifier = -1;
				else tile.card.modifier = 0;
				
				tile.topPow = tools.madmax(int(tile.topPow) + tile.card.modifier);
				tile.rightPow = tools.madmax(int(tile.rightPow) + tile.card.modifier);
				tile.bottomPow = tools.madmax(int(tile.bottomPow) + tile.card.modifier);
				tile.leftPow = tools.madmax(int(tile.leftPow) + tile.card.modifier);
			}
			
			if (checking) {
				// checking = AI purpose
				var power:uint = 0;
				if (_RULES.SAME || _RULES.SAME_WALL || _RULES.PLUS) {
					var specialFlip:Array = specialRule(tile, color);
					var alreadyFlipped:Array = [];
					for each (var flipData:Object in specialFlip) {
						var cardToFlip:Card = flipData.card as Card;
						if (!ArrayUtil.arrayContainsValue(alreadyFlipped, cardToFlip.tile.id)) alreadyFlipped.push(cardToFlip.tile.id);
						if (flipData.waveEffect) {
							var shocks:uint = flipData.waveEffect.length;
							if (shocks > 0) {
								for (var i:uint = 0; i < shocks; i++ ) {
									var wave:Array = flipData.waveEffect[i];
									for each (var comboFlipData:Object in wave) {
										cardToFlip = comboFlipData.card as Card;
										if (!ArrayUtil.arrayContainsValue(alreadyFlipped, cardToFlip.tile.id)) alreadyFlipped.push(cardToFlip.tile.id);
									}
								}
							}
						}
					}
					power = alreadyFlipped.length;
				} else {
					power = basicRule(tile, color).length;
				}
				
				return power;
			} else {
				SoundManager.playSound('se_ttriad.scd_1', true);
				animate(tile, color);
				return 0;
			}
		}
		
		public function animate(tile:Tile, color:String):void {
			//trace('animate : '+tile.id)
			var justAsec:uint = 600;
			var basicFlip:Array = [];
			var specialFlip:Array = [];
			var flipData:Object, cardToFlip:Card;
			
			if (_RULES.SAME || _RULES.SAME_WALL || _RULES.PLUS) specialFlip = specialRule(tile, color);
			else basicFlip = basicRule(tile, color);
			
			var hasCombo:Boolean = false;
			
			var flipNb:uint = specialFlip.length;
			if (flipNb > 0) {
				// au moins un coup spécial
				var alreadyFlipped:Array = [];
				for (var i:uint = 0; i < flipNb ; i++ ) {
					flipData = specialFlip[i];
					cardToFlip = flipData.card as Card;
					if (!ArrayUtil.arrayContainsValue(alreadyFlipped, cardToFlip.tile.id)) {
						if (flipData.type == "SAME" || flipData.type == "SAME_WALL") {
							var sameAnim:SameAnim = new SameAnim();
							this._SCREEN.addChild(sameAnim);
						} else if(flipData.type == "PLUS") {
							var plusAnim:PlusAnim = new PlusAnim();
							this._SCREEN.addChild(plusAnim);
						}
						
						cardToFlip.flipTo(flipData.horizon, color);
						alreadyFlipped.push(cardToFlip.tile.id);
						
						if (flipData.waveEffect) {
							SoundManager.playSound('se_ttriad.scd_15', true);
							if (flipData.waveEffect[0] && flipData.waveEffect[0].length > 0) {
								hasCombo = true;
							}
						}
					}
				}
				
				if (hasCombo) {
					var comboAnim:ComboAnim = new ComboAnim();
					var wave:Array;
					var comboFlipData:Object;
					this._SCREEN.addChild(comboAnim);
					for (i = 0; i < flipNb ; i++ ) {
						flipData = specialFlip[i];
						cardToFlip = flipData.card as Card;
						if (flipData.waveEffect) {
							// combo wave effect
							var shocks:uint = flipData.waveEffect.length;
							if (shocks > 0 && flipData.waveEffect[0].length > 0) {
								//var comboFlipData in flipData.waveEffect
								for (var j:uint = 0; j < shocks; j++ ) {
									wave = flipData.waveEffect[j];
									for (var k:uint = 0, l:uint = wave.length; k < l; k++ ) {
										comboFlipData = wave[k]
										cardToFlip = comboFlipData.card as Card;
										if (!ArrayUtil.arrayContainsValue(alreadyFlipped, cardToFlip.tile.id)) {
											setTimeout(cardToFlip.flipTo, (j+3)*400, comboFlipData.horizon, color);
											alreadyFlipped.push(cardToFlip.tile.id);
											justAsec += (j+3) * 400;
										}
									}
								}
							}
						}
					}
				}
			} else {
				for (i = 0, flipNb = basicFlip.length; i < flipNb ; i++ ) {
					flipData = basicFlip[i];
					cardToFlip = flipData.card as Card;
					cardToFlip.flip(flipData.horizon);
				}
			}
			
			// we wait for cards flips;
			setTimeout(this._SCREEN.ascensionPhase, justAsec, tile);
		}
		
		public function basicRule(tile:Tile, COLOR:String):Array {
			var cardToFlip:Array = [];
			if (tile.topTile && tile.topTile.isTaken && tile.topTile.card.color !== COLOR) {
				if (_RULES.REVERSE) {
					if (tile.topTile.bottomPow > tile.topPow) cardToFlip.push({card:tile.topTile.card, horizon:false});
				} else {
					if (tile.topTile.bottomPow < tile.topPow) cardToFlip.push({card:tile.topTile.card, horizon:false});
				}
			}
			if (tile.rightTile && tile.rightTile.isTaken && tile.rightTile.card.color !== COLOR) {
				if (_RULES.REVERSE) {
					if (tile.rightTile.leftPow > tile.rightPow) cardToFlip.push({card:tile.rightTile.card, horizon:true});
				} else {
					if (tile.rightTile.leftPow < tile.rightPow) cardToFlip.push({card:tile.rightTile.card, horizon:true});
				}
			}
			if (tile.bottomTile && tile.bottomTile.isTaken && tile.bottomTile.card.color !== COLOR) {
				if (_RULES.REVERSE) {
					if (tile.bottomTile.topPow > tile.bottomPow) cardToFlip.push({card:tile.bottomTile.card, horizon:false});
				} else {
					if (tile.bottomTile.topPow < tile.bottomPow) cardToFlip.push({card:tile.bottomTile.card, horizon:false});
				}
			}
			if (tile.leftTile && tile.leftTile.isTaken && tile.leftTile.card.color !== COLOR) {
				if (_RULES.REVERSE) {
					if (tile.leftTile.rightPow > tile.leftPow) cardToFlip.push({card:tile.leftTile.card, horizon:true});
				} else {
					if (tile.leftTile.rightPow < tile.leftPow) cardToFlip.push({card:tile.leftTile.card, horizon:true});
				}
			}
			
			return cardToFlip;
		}
		
		/*
		 * PLUS - SAME - SAME WALL
		 */
		public function specialRule(tile:Tile, COLOR:String):Array {
			var cardToFlip:Array = [];
			var same:Array = [];
			var plus:Array = [];
			// need to verify with card digit (without modifiers) instead of tile power values
			if (tile.topTile && tile.topTile.isTaken) {
				same.push( { tile:tile.topTile, value:Math.round(tile.topTile.card.bottomPow - tile.card.topPow), horizon:false } );
				plus.push( { tile:tile.topTile, value:Math.round(tile.topTile.card.bottomPow + tile.card.topPow), horizon:false } );
				if (tile.topTile.card.color !== COLOR) {
					if (_RULES.REVERSE) {
						if (tile.topTile.bottomPow > tile.topPow) cardToFlip.push({card:tile.topTile.card, horizon:false, type:'ZZ'});
					} else {
						if (tile.topTile.bottomPow < tile.topPow) cardToFlip.push({card:tile.topTile.card, horizon:false, type:'ZZ'});
					}
				}
			}
			if (tile.rightTile && tile.rightTile.isTaken) {
				same.push( { tile:tile.rightTile, value:Math.round(tile.rightTile.card.leftPow - tile.card.rightPow), horizon:true } );
				plus.push( { tile:tile.rightTile, value:Math.round(tile.rightTile.card.leftPow + tile.card.rightPow), horizon:true } );
				if (tile.rightTile.card.color !== COLOR) {
					if (_RULES.REVERSE) {
						if (tile.rightTile.leftPow > tile.rightPow) cardToFlip.push({card:tile.rightTile.card, horizon:true, type:'ZZ'});
					} else {
						if (tile.rightTile.leftPow < tile.rightPow) cardToFlip.push({card:tile.rightTile.card, horizon:true, type:'ZZ'});
					}
				}
			}
			if (tile.bottomTile && tile.bottomTile.isTaken) {
				same.push( { tile:tile.bottomTile, value:Math.round(tile.bottomTile.card.topPow - tile.card.bottomPow), horizon:false } );
				plus.push( { tile:tile.bottomTile, value:Math.round(tile.bottomTile.card.topPow + tile.card.bottomPow), horizon:false } );
				if (tile.bottomTile.card.color !== COLOR) {
					if (_RULES.REVERSE) {
						if (tile.bottomTile.topPow > tile.bottomPow) cardToFlip.push({card:tile.bottomTile.card, horizon:false, type:'ZZ'});
					} else {
						if (tile.bottomTile.topPow < tile.bottomPow) cardToFlip.push({card:tile.bottomTile.card, horizon:false, type:'ZZ'});
					}
				}
			}
			if (tile.leftTile && tile.leftTile.isTaken) {
				same.push( { tile:tile.leftTile, value:Math.round(tile.leftTile.card.rightPow - tile.card.leftPow), horizon:true } );
				plus.push( { tile:tile.leftTile, value:Math.round(tile.leftTile.card.rightPow + tile.card.leftPow), horizon:true } );
				if (tile.leftTile.card.color !== COLOR) {
					if (_RULES.REVERSE) {
						if (tile.leftTile.rightPow > tile.leftPow) cardToFlip.push({card:tile.leftTile.card, horizon:true, type:'ZZ'});
					} else {
						if (tile.leftTile.rightPow < tile.leftPow) cardToFlip.push({card:tile.leftTile.card, horizon:true, type:'ZZ'});
					}
				}
			}
			
			if (same.length > 1) {
				// need at least 2 next cards to make a plus or a same
				var loopLength:int = same.length;
				var i:uint = 0;
				var enqueue:Array = [];
				var tileComboted:Array = [];
				
				while (i < loopLength) {
					var j:uint = i+1;
					while (j < loopLength) {
						if (_RULES.SAME) {
							if (same[i].value == 0 && same[j].value == 0) {
								if (same[i].tile.card.color !== COLOR) {
									cardToFlip.push( { card:same[i].tile.card, horizon:same[i].horizon, type:'SAME', waveEffect:comboRule(same[i].tile, enqueue, 0, COLOR, tileComboted) } );
								}
								if (same[j].tile.card.color !== COLOR) {
									cardToFlip.push( { card:same[j].tile.card, horizon:same[j].horizon, type:'SAME', waveEffect:comboRule(same[j].tile, enqueue, 0, COLOR, tileComboted) } );
								}
							}
						}
						
						if (_RULES.PLUS) {
							if ((plus[i].value == plus[j].value)) {
								if (plus[i].tile.card.color !== COLOR) {
									cardToFlip.push({card:plus[i].tile.card, horizon:plus[i].horizon, type:'PLUS', waveEffect:comboRule(plus[i].tile, enqueue, 0, COLOR, tileComboted)});
								}
								if (plus[j].tile.card.color !== COLOR) {
									cardToFlip.push({card:plus[j].tile.card, horizon:plus[j].horizon, type:'PLUS', waveEffect:comboRule(plus[j].tile, enqueue, 0, COLOR, tileComboted)});
								}
							}
						}
						j++;
					}
					if (_RULES.SAME_WALL) {
						if (same[i].value == 0 && same[i].tile.card.color !== COLOR) {
							if (tile.onSameWall) {
								cardToFlip.push({card:same[i].tile.card, horizon:same[i].horizon, type:'SAME_WALL', waveEffect:comboRule(same[i].tile, enqueue, 0, COLOR, tileComboted)});
							}
						}
					}
					i++;
				}
			}
			cardToFlip.sortOn(['type'], [Array.CASEINSENSITIVE]);
			return cardToFlip;
		}
		
		public function comboRule(tile:Tile, enqueue:Array, bounce:uint, COLOR:String, tileComboted:Array):Array {
			if (!enqueue[bounce]) enqueue[bounce] = [];
			// TODO : correct combo
			if (tile.topTile && tile.topTile.isTaken && tile.topTile.card.color !== COLOR && !ArrayUtil.arrayContainsValue(tileComboted, tile.topTile.id)) {
				if (_RULES.REVERSE) {
					if (tile.topTile.bottomPow > tile.topPow) {
						enqueue[bounce].push( { card:tile.topTile.card, horizon:false } );
						tileComboted.push(tile.topTile.id);
						comboRule(tile.topTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				} else {
					if (tile.topTile.bottomPow < tile.topPow) {
						enqueue[bounce].push( { card:tile.topTile.card, horizon:false } );
						tileComboted.push(tile.topTile.id);
						comboRule(tile.topTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				}
			}
			if (tile.rightTile && tile.rightTile.isTaken && tile.rightTile.card.color !== COLOR && !ArrayUtil.arrayContainsValue(tileComboted, tile.rightTile.id)) {
				if (_RULES.REVERSE) {
					if (tile.rightTile.leftPow > tile.rightPow) {
						enqueue[bounce].push( { card:tile.rightTile.card, horizon:true } );
						tileComboted.push(tile.rightTile.id);
						comboRule(tile.rightTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				} else {
					if (tile.rightTile.leftPow < tile.rightPow) {
						enqueue[bounce].push( { card:tile.rightTile.card, horizon:true } );
						tileComboted.push(tile.rightTile.id);
						comboRule(tile.rightTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				}
			}
			if (tile.bottomTile && tile.bottomTile.isTaken && tile.bottomTile.card.color !== COLOR && !ArrayUtil.arrayContainsValue(tileComboted, tile.bottomTile.id)) {
				if (_RULES.REVERSE) {
					if (tile.bottomTile.topPow > tile.bottomPow) {
						enqueue[bounce].push( { card:tile.bottomTile.card, horizon:false } );
						tileComboted.push(tile.bottomTile.id);
						comboRule(tile.bottomTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				} else {
					if (tile.bottomTile.topPow < tile.bottomPow) {
						enqueue[bounce].push( { card:tile.bottomTile.card, horizon:false } );
						tileComboted.push(tile.bottomTile.id);	
						comboRule(tile.bottomTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				}
			}
			if (tile.leftTile && tile.leftTile.isTaken && tile.leftTile.card.color !== COLOR && !ArrayUtil.arrayContainsValue(tileComboted, tile.leftTile.id)) {
				if (_RULES.REVERSE) {
					if (tile.leftTile.rightPow > tile.leftPow) {
						enqueue[bounce].push( { card:tile.leftTile.card, horizon:true } );
						tileComboted.push(tile.leftTile.id);
						comboRule(tile.leftTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				} else {
					if (tile.leftTile.rightPow < tile.leftPow) {
						enqueue[bounce].push( { card:tile.leftTile.card, horizon:true } );
						tileComboted.push(tile.leftTile.id);
						comboRule(tile.leftTile, enqueue, bounce+1, COLOR, tileComboted);
					}
				}
			}
			
			return enqueue;
		}
		
		public function get RULES():Object 
		{
			return _RULES;
		}
		
		public function set RULES(value:Object):void 
		{
			_RULES = value;
		}
		
		public function get SCREEN():BaseMatchScreen 
		{
			return _SCREEN;
		}
		
		public function set SCREEN(value:BaseMatchScreen):void 
		{
			_SCREEN = value;
		}
	}

}