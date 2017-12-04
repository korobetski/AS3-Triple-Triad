package tto.screens {
	import flash.utils.setTimeout;
	import tto.anims.BlueWinAnim;
	import tto.anims.DrawAnim;
	import tto.anims.RedWinAnim;
	import tto.anims.SuddenDeathAnim;
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
	public class CCGroupMatchScreen extends PVEMatchScreen {
		private var matches:Array = []
		public var STEP:uint;
		private var _messages:Object;
		
		public function CCGroupMatchScreen() {
			super();
			
			this.matches = [
			{npc:new NPC( {
				name:"STR_NPC_JACK",
				iconID:'jack',
				rules:[tripleTriadRules.RULE_ALL_OPEN, tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_ELEMENTAL],
				fetishesCards:[],
				cards:[8, 11, 15, 17, 18, 19, 20, 24, 25, 26, 27, 29, 30, 85],
				matchFee:0,
				level:NPC.LEVEL_INITIATE,
				MGPReward: { w:20, d:11, l:5 },
				itemRewards:[ { type:"card", card:29, rate:0.25 }, { type:"card", card:30, rate:0.25 }, { type:"card", card:85, rate:0.01 }]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				name:"STR_NPC_CLUB",
				iconID:'club',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_RANDOM, tripleTriadRules.RULE_ELEMENTAL],
				fetishesCards:[],
				cards:[8, 11, 15, 17, 18, 19, 20, 24, 25, 26, 27, 29, 30, 86],
				matchFee:0,
				level:NPC.LEVEL_INITIATE,
				MGPReward: { w:25, d:13, l:6 },
				itemRewards:[ { type:"card", card:26, rate:0.33 }, { type:"card", card:27, rate:0.33 }, { type:"card", card:86, rate:0.02 }]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				name:"STR_NPC_DIAMOND",
				iconID:'diamond',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_SAME, tripleTriadRules.RULE_PLUS],
				fetishesCards:[],
				cards:[15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 29, 30],
				matchFee:0,
				level:NPC.LEVEL_AVERAGE,
				MGPReward: { w:36, d:18, l:9 },
				itemRewards:[{ type:"potion", potion:'MGP_BOOST', rate:1 }, {type:"card",  card:67, rate:0.1 }, { type:"card", card:68, rate:0.1 }]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				name:"STR_NPC_SPADE",
				iconID:'spade',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_RANDOM, tripleTriadRules.RULE_PLUS],
				fetishesCards:[],
				cards:[15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 29, 30, 98],
				matchFee:15,
				level:NPC.LEVEL_AVERAGE,
				MGPReward: { w:40, d:20, l:10 },
				itemRewards:[{ type:"card", card:98, rate:0.01 }]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				name:"STR_NPC_JOCKER",
				iconID:'jocker',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_RANDOM, tripleTriadRules.RULE_SAME, tripleTriadRules.RULE_SAME_WALL, tripleTriadRules.RULE_PLUS, tripleTriadRules.RULE_SUDDEN_DEATH],
				fetishesCards:[],
				cards:[33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,91],
				matchFee:0,
				level:NPC.LEVEL_ADVANCED,
				MGPReward: { w:54, d:27, l:13 },
				itemRewards:[{ type:"potion", potion:'MGP_BOOST', rate:1 }, { type:"card", card:91, rate:0.01 }]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				name:"STR_NPC_HEART",
				iconID:'heart',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_RANDOM, tripleTriadRules.RULE_SAME, tripleTriadRules.RULE_PLUS],
				fetishesCards:[],
				cards:[33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,89],
				matchFee:0,
				level:NPC.LEVEL_EXPERT,
				MGPReward: { w:110, d:40, l:20 },
				itemRewards:[{ type:"potion", potion:'MGP_BOOST', rate:1 }, {type:"card",  card:89, rate:0.01 }]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				name:"STR_NPC_KING",
				iconID:'king',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_RANDOM, tripleTriadRules.RULE_SAME, tripleTriadRules.RULE_SAME_WALL, tripleTriadRules.RULE_PLUS],
				fetishesCards:[],
				cards:[33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,108],
				matchFee:0,
				level:NPC.LEVEL_EXPERT,
				MGPReward: { w:110, d:40, l:20 },
				itemRewards:[{ type:"potion", potion:'MGP_BOOST', rate:1 }, { type:"card", card:108, rate:0.01 }]
			}), messages:{start:"", draw:"", win:"", lose:""}}
		]
		}
		
		override protected function initialize():void {
			if (!STEP) STEP = 0;
			this._NPC = matches[STEP].npc;
			_messages = matches[STEP].messages;
			this.RULES = this._NPC.gameRules;
			this.RED_PLAYER_NAME = i18n.gettext(this._NPC.name);
			if (!this.BLUE_CARDS) this.BLUE_CARDS = [];
			if (!this.RED_CARDS) this.RED_CARDS = [];
			
			 super.initialize();
			 
			 pof.randomRolls();
		}
		
		override protected function opponentPhase():void {
			setTimeout(AI, 1000+tools.rand(4)*1000);
		}
		
		override protected function letsGetStarted():void {
			super.letsGetStarted();
			talk(_messages.start);
		}
		
		private function talk(message:String):void {
			if (message !== "") {
				var talk:TalkAnim = new TalkAnim(message, this.RED_PLAYER_NAME);
				this.addChild(talk);
			}
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
				talk(_messages.draw);
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
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_DRAW'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, NEXT_STEP:this.STEP});
				}
			} else {
				if (scores.BLUE > scores.RED) {
					talk(_messages.win);
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
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_WIN'), MGP:MGPReward, XP:XPReward, items:rewardItems, achievements:NewAchiev, NEXT_STEP:this.STEP+1});
				} else {
					talk(_messages.lose);
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
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_LOSE'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, NEXT_STEP:0});
				}
			}
		}
		
		override protected function suddenDeathDispatcher():void {
			var bCards:Object = bluePlayer.getCardIdsByColor();
			var rCards:Object = redPlayer.getCardIdsByColor();
			this.dispatchEventWith('cc_sudden_death', false, { RULES:RULES, BLUE_CARDS:bCards.BLUE.concat(rCards.BLUE), RED_CARDS:bCards.RED.concat(rCards.RED), RED_PLAYER_NAME:i18n.gettext(this._NPC.name), _NPC:this._NPC, SUDDEN_DEATH_NEXT:true, timeline:timeline, STEP:this.STEP });
		}
		
		override protected function rematch(params:Object):void {
			var rematchPanel:CCGroupRematchPanel = new CCGroupRematchPanel(params);
			rematchPanel.x = board.x+16;
			rematchPanel.y = board.y+16;
			addChild(rematchPanel)
		}
		
	}

}