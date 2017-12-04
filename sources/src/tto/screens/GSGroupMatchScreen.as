package tto.screens {
	import flash.utils.setTimeout;
	import tto.anims.BlueWinAnim;
	import tto.anims.DrawAnim;
	import tto.anims.RedWinAnim;
	import tto.anims.SuddenDeathAnim;
	import tto.anims.TalkAnim;
	import tto.datas.Achievements;
	import tto.datas.BoosterItem;
	import tto.datas.cards;
	import tto.datas.NPC;
	import tto.datas.Save;
	import tto.datas.tripleTriadRules;
	import tto.Game;
	import tto.utils.i18n;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	/**
	 * ...
	 * @author Mao
	 */
	public class GSGroupMatchScreen extends PVEMatchScreen {
		private var matches:Array = []
		public var STEP:uint;
		private var _messages:Object;
		
		public function GSGroupMatchScreen() {
			super();
			
			this.matches = [
			{npc:new NPC( {
				id:1,
				name:'STR_NPC_TT_Master',
				iconID:'tt-master',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_ALL_OPEN],
				fetishesCards:[],
				cards:cards.getCardsByRarities([1], 'ff14_'),
				level:NPC.LEVEL_NOVICE,
				matchFee:0,
				MGPReward: { w:22, d:8, l:3 },
				difficulty:0,
				itemRewards:[ { type:"potion", potion:'MGP_BOOST', rate:0.02 }, { type:"card", card:4, rate:0.25 }, {type:"card",  card:13, rate:0.20 } ]
			}), messages:{start:"Let's see what you got in a serious match.", draw:"", win:"Good luck for the future.", lose:"It was perhaps a little early..."}},
			{npc:new NPC( {
				id:2,
				name:'STR_NPC_Jonas',
				iconID:'jonas',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_ALL_OPEN, tripleTriadRules.RULE_SAME],
				fetishesCards:[],
				cards:[3, 4, 11, 15, 20, 21, 22, 23, 24, 25, 26],
				level:NPC.LEVEL_NOVICE,
				matchFee:0,
				MGPReward: { w:22, d:8, l:3 },
				difficulty:3,
				itemRewards:[{type:"card", card:15, rate:0.25}, {type:"card", card:20, rate:0.20}]
			}), messages:{start:"I will not let me!", draw:"", win:"You are strong, but this is only the beginning.", lose:"YATAAH !"}},
			{npc:new NPC( {
				id:3,
				name:'STR_NPC_Guhtwint',
				iconID:'guhtwint',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_THREE_OPEN, tripleTriadRules.RULE_PLUS],
				fetishesCards:[],
				cards:[41, 27, 13, 11, 38, 26, 23, 39, 40, 41],
				level:NPC.LEVEL_INITIATE,
				matchFee:0,
				MGPReward: { w:32, d:12, l:4 },
				difficulty:3,
				itemRewards:[{type:"card", card:9, rate:0.20}, {type:"card", card:13, rate:0.20}, {type:"card", card:27, rate:0.125}]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				id:4,
				name:'STR_NPC_Aurifort',
				iconID:'aurifort',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_THREE_OPEN, tripleTriadRules.RULE_RANDOM],
				fetishesCards:[],
				cards:cards.getCardsByRarities([2,3], 'ff14_'),
				level:NPC.LEVEL_AVERAGE,
				matchFee:0,
				MGPReward: { w:50, d:20, l:7 },
				difficulty:3,
				itemRewards:[{type:"card", card:12, rate:0.20}, {type:"card", card:27, rate:0.125}]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				id:5,
				name:'STR_NPC_Ruhtwyda',
				iconID:'ruhtwyda',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_ASCENSION, tripleTriadRules.RULE_ALL_OPEN],
				fetishesCards:[59, 56, 47],
				cards:[58, 50, 57, 45],
				level:NPC.LEVEL_AVERAGE,
				matchFee:0,
				MGPReward: { w:59, d:0, l:8 },
				difficulty:9,
				itemRewards:[{type:"card", card:45, rate:0.15}, {type:"card", card:50, rate:0.125}]
			}), messages:{start:"", draw:"", win:"", lose:""}},
			{npc:new NPC( {
				id:6,
				name:'STR_NPC_King_Elmer',
				iconID:'elmer',
				rules:[tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_PLUS],
				fetishesCards:[61, 50, 45],
				cards:[62, 39, 49, 41],
				level:NPC.LEVEL_EXPERT,
				matchFee:0,
				MGPReward: { w:617, d:46, l:17 },
				difficulty:7,
				itemRewards:[ { type:"potion", potion:'MGP_BOOST', rate:0.25 }, {type:"card", card:45, rate:0.125}, {type:"card", card:61, rate:0.05}, {type:"booster", booster:BoosterItem.BOOSTER_TYPE_PLATINUM, rate:0.25}]
			}), messages:{start:"It's the last lap, give everything you have.", draw:"", win:"I admit my defeat...", lose:"Come see me in hundred years."}}
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
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_WIN'), MGP:MGPReward, XP:XPReward, items:rewardItems, achievements:NewAchiev, NEXT_STEP:uint(this.STEP+1)});
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
			this.dispatchEventWith('gs_sudden_death', false, { RULES:RULES, BLUE_CARDS:bCards.BLUE.concat(rCards.BLUE), RED_CARDS:bCards.RED.concat(rCards.RED), RED_PLAYER_NAME:i18n.gettext(this._NPC.name), _NPC:this._NPC, SUDDEN_DEATH_NEXT:true, timeline:timeline, STEP:this.STEP });
		}
		
		override protected function rematch(params:Object):void {
			var rematchPanel:GSGroupRematchPanel = new GSGroupRematchPanel(params);
			rematchPanel.x = board.x+16;
			rematchPanel.y = board.y+16;
			addChild(rematchPanel)
		}
		
	}

}