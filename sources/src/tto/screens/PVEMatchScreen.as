package tto.screens {
	import flash.utils.setTimeout;
	import tto.anims.BlueWinAnim;
	import tto.anims.DrawAnim;
	import tto.anims.RedWinAnim;
	import tto.anims.SuddenDeathAnim;
	import tto.datas.Achievements;
	import tto.datas.NPC;
	import tto.datas.Save;
	import tto.datas.tripleTriadRules;
	import tto.display.Card;
	import tto.display.Tile;
	import tto.Game;
	import tto.utils.i18n;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	/**
	 * ...
	 * @author Mao
	 */
	public class PVEMatchScreen extends BaseMatchScreen {
		public var _NPC:NPC;
		
		public function PVEMatchScreen() {
			super();
		}
		
		override protected function initialize():void {
			RED_PLAYER_NAME = i18n.gettext(_NPC.name);
			super.initialize();
		}
		
		override public function deckSelectionPhase():void {
			super.deckSelectionPhase();
			if (RED_CARDS.length < 5) this.RED_CARDS = this._NPC.getRandomCards();
			redPlayer.drawCards(RED_CARDS);
		}
		
		override protected function opponentPhase():void 
		{
			super.opponentPhase();
			setTimeout(AI, 1000+tools.rand(4)*1000);
		}
		
		override protected function endGame():void {
			// END GAME
			redPlayer.razTimer();
			bluePlayer.razTimer();
			
			var scores:Object = updateScores();
			var MGPReward:uint = 0;
			var XPReward:uint = 0;
			var MGPBoon:Boolean = false;
			var XPBoon:Boolean = false;
			var Achiev:Achievements;
			var NewAchiev:Array;
			if (Game.PROFILE_DATAS.BOONS) {
				if (int(Game.PROFILE_DATAS.BOONS.MGP) > 0) MGPBoon = true;
				if (int(Game.PROFILE_DATAS.BOONS.XP) > 0) XPBoon = true;
			} else {
				Game.PROFILE_DATAS.BOONS = {MGP:0, XP:0, LUCK:0};
			}
			if (scores.BLUE == scores.RED) {
				if (RULES.SUDDEN_DEATH) {
					var sda:SuddenDeathAnim = new SuddenDeathAnim();
					this.addChild(sda);
					setTimeout(suddenDeathDispatcher, 1600);
				} else {
					var drawAnim:DrawAnim = new DrawAnim();
					this.addChild(drawAnim);
					Game.PROFILE_DATAS.STATS.DRAWS++;
					Game.PROFILE_DATAS.ENDED_MATCHES += 1;
					if (this._NPC) {
						if (MGPBoon) {
							MGPReward = _NPC.MGPReward.d + tools.rand(10) + Math.round(_NPC.MGPReward.d * 20 / 100);
							Game.PROFILE_DATAS.BOONS.MGP -= 1;
						} else MGPReward = _NPC.MGPReward.d + tools.rand(10);
						
						if (XPBoon) {
							XPReward = _NPC.XPReward.d + Math.round(_NPC.XPReward.d * 20 / 100);
							Game.PROFILE_DATAS.BOONS.XP -= 1;
						} else XPReward = _NPC.XPReward.d;
						
						Game.PROFILE_DATAS.MGP += MGPReward;
						Game.PROFILE_DATAS.XP += XPReward;
					}
					Achiev = new Achievements();
					NewAchiev = Achiev.check();
					Save.save(Game.PROFILE_DATAS);
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_DRAW'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, NPC:this._NPC, RULES:this.RULES});
				}
			} else {
				if (scores.BLUE > scores.RED) {
					SoundManager.playSound('se_ttriad.scd_7', true);
					var bwAnim:BlueWinAnim = new BlueWinAnim();
					this.addChild(bwAnim);
					
					Game.PROFILE_DATAS.STATS.WINS++;
					Game.PROFILE_DATAS.ENDED_MATCHES += 1;
					
					for each (var rule:String in boardRules.activeRules) {
						if (rule !== null && rule !== 'null') {
							if (Game.PROFILE_DATAS.RULES_W[rule]) Game.PROFILE_DATAS.RULES_W[rule] += 1;
							else Game.PROFILE_DATAS.RULES_W[rule] = 1;
						}
					}
					
					if (this._NPC) {
						if (Game.PROFILE_DATAS.NPC_W[this._NPC.iconID]) Game.PROFILE_DATAS.NPC_W[this._NPC.iconID] += 1;
						else Game.PROFILE_DATAS.NPC_W[this._NPC.iconID] = 1;
						npcs_total();
						if (MGPBoon) {
							MGPReward = _NPC.MGPReward.w + tools.rand(20) + Math.round(_NPC.MGPReward.w * 20 / 100);
							Game.PROFILE_DATAS.BOONS.MGP -= 1;
						} else MGPReward = _NPC.MGPReward.w + tools.rand(20);
						
						if (XPBoon) {
							XPReward = _NPC.XPReward.w + Math.round(_NPC.XPReward.w * 20 / 100);
							Game.PROFILE_DATAS.BOONS.XP -= 1;
						} else XPReward = _NPC.XPReward.w;
						
						Game.PROFILE_DATAS.MGP += MGPReward;
						Game.PROFILE_DATAS.XP += XPReward;
						var rewardItems:Array = _NPC.getRewardItems();
						if (rewardItems.length > 0) {
							for each(var rewardItem:Object in rewardItems) {
								Game.PROFILE_DATAS.BAG.push(rewardItem.__toJSON());
							}
						}
					}
					Achiev = new Achievements();
					NewAchiev = Achiev.check();
					Save.save(Game.PROFILE_DATAS);
					InventoryScreen.sortBag();
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_WIN'), MGP:MGPReward, XP:XPReward, items:rewardItems, achievements:NewAchiev, NPC:this._NPC, RULES:this.RULES});
				} else {
					SoundManager.playSound('se_ttriad.scd_8', true);
					var rwAnim:RedWinAnim = new RedWinAnim();
					this.addChild(rwAnim);
					
					Game.PROFILE_DATAS.STATS.DEFEATS++;
					Game.PROFILE_DATAS.ENDED_MATCHES += 1;
					if (this._NPC) {
						if (MGPBoon) {
							MGPReward = _NPC.MGPReward.l + tools.rand(5) + Math.round(_NPC.MGPReward.l * 20 / 100);
							Game.PROFILE_DATAS.BOONS.MGP -= 1;
						} else MGPReward = _NPC.MGPReward.l + tools.rand(5);
						
						if (XPBoon) {
							XPReward = _NPC.XPReward.l + Math.round(_NPC.XPReward.l * 20 / 100);
							Game.PROFILE_DATAS.BOONS.XP -= 1;
						} else XPReward = _NPC.XPReward.l;
						
						Game.PROFILE_DATAS.MGP += MGPReward;
						Game.PROFILE_DATAS.XP += XPReward;
					}
					Achiev = new Achievements();
					NewAchiev = Achiev.check();
					Save.save(Game.PROFILE_DATAS);
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_LOSE'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, NPC:this._NPC, RULES:this.RULES});
				}
			}
		}
		protected function npcs_total():void {
			var total:uint = 0;
			for (var npc_id:String in Game.PROFILE_DATAS.NPC_W) {
				total += uint(Game.PROFILE_DATAS.NPC_W[npc_id]);
			}
			Game.PROFILE_DATAS["NPC_W_TOTAL"] = total;
		}
		
		override protected function suddenDeathDispatcher():void {
			var bCards:Object = bluePlayer.getCardIdsByColor();
			var rCards:Object = redPlayer.getCardIdsByColor();
			this.dispatchEventWith('sudden_death', false, { RULES:RULES, BLUE_CARDS:bCards.BLUE.concat(rCards.BLUE), RED_CARDS:bCards.RED.concat(rCards.RED), RED_PLAYER_NAME:i18n.gettext(this._NPC.name), _NPC:this._NPC, SUDDEN_DEATH_NEXT:true, timeline:timeline });
		}
		
		// AI --------------------------------------------
		protected function AI():void {
			var remainingCards:Array = redPlayer.getRemainingCards();
			// ORDER RULES
			if (RULES.ORDER !== tripleTriadRules.RULE_DEFAULT_ORDER) {
				if (RULES.ORDER == tripleTriadRules.RULE_ORDER) remainingCards = [redPlayer.getRemainingCards()[0]];
				else if (RULES.ORDER == tripleTriadRules.RULE_CHAOS)  remainingCards = [remainingCards[tools.rand(remainingCards.length-1)]];
			}
			
			var remainingTiles:Array = board.getRemainingTiles();
			var powers:Array = [];
			var card:Card, tile:Tile, pow:uint, cover:uint;
			// check each remaining possibilities
			var i:uint = 0, j:uint = 0, k:uint =0, l:uint = 0;
			for (i = 0, l = remainingCards.length; i < l ; i++) {
				card = remainingCards[i]
				for (j = 0, k = remainingTiles.length; j < k ; j++) {
					tile = remainingTiles[j]
					tile.card = card;
					pow = CORE.applyRules(tile, timeline[turn], true);
					// calculate the defenssive power
					cover = 0;
					if (tile.topTile && !tile.topTile.isTaken) cover += (RULES.REVERSE) ? (10-tile.topPow) : tile.topPow;
					else cover += 10;
					if (tile.rightTile && !tile.rightTile.isTaken) cover += (RULES.REVERSE) ? (10-tile.rightPow) : tile.rightPow;
					else cover += 10;
					if (tile.bottomTile && !tile.bottomTile.isTaken) cover += (RULES.REVERSE) ? (10-tile.bottomPow) : tile.bottomPow;
					else cover += 10;
					if (tile.leftTile && !tile.leftTile.isTaken) cover += (RULES.REVERSE) ? (10-tile.leftPow) : tile.leftPow;
					else cover += 10;
					
					if (RULES.TYPE_RULE == tripleTriadRules.RULE_ELEMENTAL) tile.card.modifier = 0;
					tile.card = null;// VERY IMPORTANT
					powers.push( { card:card, tile:tile, power:pow, cover:cover } );
				}
			}
			
			powers.sortOn(['power', 'cover'], [Array.DESCENDING, Array.DESCENDING]);
			var bestPow:uint = powers[0].power;
			
			if (bestPow > 0) {
				var bestMoves:Array = []
				var move:Object;
				for (i = 0, l = powers.length; i < l ; i++) {
					move = powers[i]
					if (move.power == bestPow) bestMoves.push(move);
				}
				var rand:uint = tools.rand(bestMoves.length-1)
				card = bestMoves[rand].card;
				tile = bestMoves[rand].tile;
			} else {
				if (this.turn > 5) {
					card = powers[0].card;
					tile = powers[0].tile;
				} else {
					if (tools.rand(1)) {
						card = powers[0].card;
						tile = powers[0].tile;
					} else {
						card = powers[powers.length -1].card;
						tile = powers[powers.length -1].tile;
					}
				}
			}
			
			tile.card = card;
			var absoluteX:int = -card.parent.x - card.parent.parent.x +tile.x + this.board.x;
			var absoluteY:int = -card.parent.y - card.parent.parent.y +tile.y + this.board.y;
			card.fly(absoluteX, absoluteY);
			card.draggable = false;
			CORE.applyRules(tile, timeline[turn]);
		}
	}

}