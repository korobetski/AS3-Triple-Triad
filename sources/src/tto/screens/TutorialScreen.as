package tto.screens {
	import flash.utils.setTimeout;
	import tto.anims.BlueWinAnim;
	import tto.anims.DrawAnim;
	import tto.anims.RedWinAnim;
	import tto.anims.TalkAnim;
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
	public class TutorialScreen extends PVEMatchScreen {
		private var helpTexts:Array = [
			"Triple Triad is played by placing cards on a three-by-three grid.\nThe player to act first is decided at random.",
			"A variety of rules exist that change how the game is played, but let's start you off with an easy one - All Open! This rule allows you to see all of your opponent's cards.",
			"I'll go first this time to show you how it's done. I'll also extend the turn limit so you have plenty of time to think!",
			"Now it's your turn! Place a card in one of the empty spaces adjacent to my card.",
			"The numbers you can see each correspond to one side of the card. Try choosing a card with a bigger number than mine on the adjacent side!",
			"See how my card changed color?\nThis means you have \"captured\" the card.",
			"During your turn, even if you play a card with a smaller number, it will not result in that card being captured.",
			"Try to capture and control more cards than your opponent!\nThe match will end when all spaces on the grid have been filled.",
			"And the match is over! Whoever controls the most cards at the end of the game is the winnner!"
		]
		
		public function TutorialScreen() {
			super();
			
			this._NPC = new NPC({
				id:1,
				name:'STR_NPC_TT_Master',
				iconID:'tt-master',
				rules:[tripleTriadRules.RULE_ALL_OPEN],
				fetishesCards:[2, 4, 5, 7, 13],
				cards:[],
				level:NPC.LEVEL_NOVICE,
				matchFee:0,
				MGPReward: { w:4, d:2, l:1 },
				difficulty:0,
				itemRewards:[ { potion:'MGP_BOOST', rate:0.01 }, { card:4, rate:0.12 }, { card:13, rate:0.10 } ]
			});
			
			this.RULES = this._NPC.gameRules;
			this.RED_PLAYER_NAME = i18n.gettext("STR_NPC_TT_Master");
			this.RED_CARDS = this._NPC.getRandomCards();
			this.BLUE_CARDS = [1, 3, 6, 7, 10];
		}
		
		override public function deckSelectionPhase():void {
			bluePlayer.timer = 60;
			redPlayer.timer = 60;
			
			if (!SoundManager._isPlaying) SoundManager.shuffleLoop();
			bluePlayer.drawCards(BLUE_CARDS);
			
			pof.rolls = [0,1,0];
			setTimeout(openPhase, 1400);
		}
		
		override protected function opponentPhase():void {
			if (turn == 1) {
				talk(0);
				setTimeout(talk, 6100, 1);
				setTimeout(talk, 12200, 2);
				setTimeout(AI, 18300);
			} else if (turn == 2) {
				setTimeout(talk, 6100, 3);
				setTimeout(talk, 12200, 4);
			} else if (turn == 3) {
				var scores:Object = updateScores();
				if (scores.BLUE > 5)
					talk(5);
				setTimeout(talk, 6100, 6);
				setTimeout(AI, 12200);
			} else if (turn == 4) {
				talk(7);
				setTimeout(AI, 6100);
			} else if (turn == 9) {
				setTimeout(talk, 3100, 8);
				setTimeout(AI, 3000);
			} else {
				setTimeout(AI, 1000+tools.rand(4)*1000);
			}
		}
		
		private function talk(step:uint):void {
			var talk:TalkAnim = new TalkAnim(helpTexts[step], i18n.gettext("STR_NPC_TT_Master"));
			this.addChild(talk);
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
				var drawAnim:DrawAnim = new DrawAnim();
				this.addChild(drawAnim);
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
				
				setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_DRAW'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, NPC:this._NPC, RULES:this.RULES, NEXT_SCREEN:"HELP_SCREEN"});
				
			} else {
				if (scores.BLUE > scores.RED) {
					SoundManager.playSound('se_ttriad.scd_7', true);
					var bwAnim:BlueWinAnim = new BlueWinAnim();
					this.addChild(bwAnim);
					
					for each (var rule:String in boardRules.activeRules) {
						if (rule !== null && rule !== 'null') {
							if (Game.PROFILE_DATAS.RULES_W[rule]) Game.PROFILE_DATAS.RULES_W[rule] += 1;
							else Game.PROFILE_DATAS.RULES_W[rule] = 1;
						}
					}
					
					if (this._NPC) {
						if (Game.PROFILE_DATAS.NPC_W[this._NPC.iconID]) Game.PROFILE_DATAS.NPC_W[this._NPC.iconID] += 1;
						else Game.PROFILE_DATAS.NPC_W[this._NPC.iconID] = 1;
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
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_WIN'), MGP:MGPReward, XP:XPReward, items:rewardItems, achievements:NewAchiev, NPC:this._NPC, RULES:this.RULES, NEXT_SCREEN:"HELP_SCREEN"});
				} else {
					SoundManager.playSound('se_ttriad.scd_8', true);
					var rwAnim:RedWinAnim = new RedWinAnim();
					this.addChild(rwAnim);
					
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
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_LOSE'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, NPC:this._NPC, RULES:this.RULES, NEXT_SCREEN:"HELP_SCREEN"});
				}
			}
		}
		
		override protected function rematch(params:Object):void {
			var rematchPanel:TutorialRematchPanel = new TutorialRematchPanel(params);
			rematchPanel.x = board.x+16;
			rematchPanel.y = board.y+16;
			addChild(rematchPanel)
		}
		
		// AI --------------------------------------------
		override protected function AI():void {
			var remainingCards:Array = redPlayer.getRemainingCards();
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
					if (tile.topTile && !tile.topTile.isTaken) cover += tile.topPow;
					else cover += 10;
					if (tile.rightTile && !tile.rightTile.isTaken) cover += tile.rightPow;
					else cover += 10;
					if (tile.bottomTile && !tile.bottomTile.isTaken) cover += tile.bottomPow;
					else cover += 10;
					if (tile.leftTile && !tile.leftTile.isTaken) cover += tile.leftPow;
					else cover += 10;
					
					tile.card = null;// VERY IMPORTANT
					powers.push( { card:card, tile:tile, power:pow, cover:cover } );
				}
			}
			
			powers.sortOn(['power', 'cover'], [Array.DESCENDING, Array.DESCENDING]);
			// c'est le tuto, le pnj jour toujours la pire solution
			card = powers[powers.length -1].card;
			tile = powers[powers.length -1].tile;
			
			tile.card = card;
			var absoluteX:int = -card.parent.x - card.parent.parent.x +tile.x + this.board.x;
			var absoluteY:int = -card.parent.y - card.parent.parent.y +tile.y + this.board.y;
			card.fly(absoluteX, absoluteY);
			card.draggable = false;
			CORE.applyRules(tile, timeline[turn]);
		}
	}

}