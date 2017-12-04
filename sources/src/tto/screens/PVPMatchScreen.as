package tto.screens {
	import com.adobe.utils.ArrayUtil;
	import flash.utils.setTimeout;
	import starling.events.Event;
	import tto.anims.BlueWinAnim;
	import tto.anims.DrawAnim;
	import tto.anims.PileOuFace;
	import tto.anims.RandomAnim;
	import tto.anims.RedWinAnim;
	import tto.anims.SuddenDeathAnim;
	import tto.anims.SwapAnim;
	import tto.datas.Achievements;
	import tto.datas.NPC;
	import tto.datas.Save;
	import tto.datas.tripleTriadRules;
	import tto.display.Card;
	import tto.display.Tile;
	import tto.Game;
	import tto.net.Socket;
	import tto.utils.i18n;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	/**
	 * ...
	 * @author Mao
	 */
	public class PVPMatchScreen extends BaseMatchScreen {
		public var ID:String;
		public var HOST:Boolean;
		public var OPPONENT_READY:Boolean;
		private var ELEMENTS_OK:Boolean;
		private var POF_OK:Boolean;
		private var SWAP_OK:Boolean;
		private var WAIT_FOR_POF:Boolean;
		private var WAIT_FOR_ELEMENT:Boolean;
		private var WAIT_FOR_SWAP:Boolean;
		private var SWAP_CARDS:Array;
		
		public function PVPMatchScreen() {
			super();
		}
		
		override protected function initialize():void {
			super.initialize();
			
			Game.MATCHES[this.ID].screen = this;
			POF_OK = false;
			ELEMENTS_OK = false;
			SWAP_OK = false;
			OPPONENT_READY = false;
			if (HOST) {
				setTimeout(ready, 1000);
			}
		}
		
		private function ready():void {
			Socket.send('<ready toroom="' + ID + '"/>');
		}
		
		override public function deckSelectionPhase():void {
			if (!SoundManager._isPlaying) SoundManager.shuffleLoop();
			
			if (RED_CARDS.length < 5) {
				// on attends que le joueur d'en face choissise ses cartes.
			}
			if (SUDDEN_DEATH_NEXT) {
				bluePlayer.drawCards(BLUE_CARDS);
				openPhase();
			} else {
				if (RULES.RANDOM) {
					var randomAnim:RandomAnim = new RandomAnim();
					this.addChild(randomAnim);
					
					var randomizer:Array = ArrayUtil.copyArray(Game.PROFILE_DATAS.CARDS);
					var randomCards:Array = [];
					if (randomizer.length == 5) {
						randomCards = randomizer;
					} else {
						while (randomCards.length < 5 ) {
							var rand:uint = (randomizer.length > 1) ? (randomizer.splice(tools.rand(randomizer.length-1), 1)[0]) : (randomizer[0]);
							randomCards.push(rand);
						}
					}
					BLUE_CARDS = randomCards;
					bluePlayer.drawCards(BLUE_CARDS);
					
					Socket.send('<setCards toroom="'+String(this.ID)+'"><from>'+String(Game.PROFILE_DATAS.USERNAME)+'</from><c1>'+uint(BLUE_CARDS[0])+'</c1><c2>'+uint(BLUE_CARDS[1])+'</c2><c3>'+uint(BLUE_CARDS[2])+'</c3><c4>'+uint(BLUE_CARDS[3])+'</c4><c5>'+uint(BLUE_CARDS[4])+'</c5></setCards>');
					
					if (bluePlayer.isReady && redPlayer.isReady) setTimeout(hostingPhase, 1400);
				} else {
					deckSelector = new DeckSelector();
					deckSelector.addEventListener(Event.COMPLETE, deckSelected);
					addChild(deckSelector);
					deckSelector.x = board.x+16;
					deckSelector.y = board.y-32;
				}
			}
		}
		
		public function setOpponentCards(cardArray:Array):void {
			RED_CARDS = cardArray;
			redPlayer.drawCards(RED_CARDS);
			if (bluePlayer.isReady && redPlayer.isReady) setTimeout(hostingPhase, 1400);
		}
		
		override protected function deckSelected(e:Event, deck:Object):void {
			BLUE_CARDS = deck.cards;
			bluePlayer.drawCards(BLUE_CARDS);
			Socket.send('<setCards toroom="'+String(this.ID)+'"><from>'+String(Game.PROFILE_DATAS.USERNAME)+'</from><c1>'+uint(BLUE_CARDS[0])+'</c1><c2>'+uint(BLUE_CARDS[1])+'</c2><c3>'+uint(BLUE_CARDS[2])+'</c3><c4>'+uint(BLUE_CARDS[3])+'</c4><c5>'+uint(BLUE_CARDS[4])+'</c5></setCards>');
			
			this.removeChild(deckSelector);
			if (bluePlayer.isReady && redPlayer.isReady) hostingPhase();
		}
		
		private function hostingPhase():void {
			var legacyElements:Object = { earth:7, fire:2, holy:6, ice:4, lightning:3, poison:5, water:1, wind:8 };
			if (this.HOST) {
				// on dispose des éléments si la règle est effective
				if (RULES.TYPE_RULE == tripleTriadRules.RULE_ELEMENTAL) {
					board.elements();
					var sco_req:String = '<elements toroom="' + this.ID + '">';
					for (var i:int=0; i<9; i++) {
						sco_req += '<p' + (i+1) + '>' + ((legacyElements[board.tiles[i].element]) ? legacyElements[board.tiles[i].element] : 0)  + '</p' + (i+1) + '>';
					}
					sco_req+='</elements>';
					Socket.send(sco_req);
					ELEMENTS_OK = true;
				}
				pof = new PileOuFace();
				pof.randomRolls();
				Socket.send('<initiative toroom="'+this.ID+'">'+((pof.blueOrRed == 'blue') ? BLUE_PLAYER_NAME : RED_PLAYER_NAME )+'</initiative>');
				POF_OK = true;
				openPhase()
			} else {
				if (RULES.TYPE_RULE == tripleTriadRules.RULE_ELEMENTAL) {
					if (ELEMENTS_OK) {
						openPhase();
					} else {
						// on doit attendre de recevoir les élements
						WAIT_FOR_ELEMENT = true;
					}
				} else {
					openPhase();
				}
			}
		}
		
		public function setPof(playerColor:String):void {
			pof = new PileOuFace();
			pof.rolls = (playerColor == 'red') ? [0, 0, 0] : [1,1,1];
			POF_OK = true;
			if (WAIT_FOR_POF == true) {
				pileOuFace()
			}
		}
		
		public function setElements(elements:Array):void {
			board.elements(elements);
			ELEMENTS_OK = true;
			if (WAIT_FOR_ELEMENT == true) {
				openPhase()
			}
		}
		
		public function setSwap(swap:Array):void {
			SWAP_CARDS = swap;
			if (WAIT_FOR_SWAP == true) {
				bluePlayer.swapCardWith(bluePlayer.getCardAt(SWAP_CARDS[0].index), SWAP_CARDS[0].id);
				redPlayer.swapCardWith(redPlayer.getCardAt(SWAP_CARDS[1].index), SWAP_CARDS[1].id);
				SWAP_OK = true;
				if (POF_OK) setTimeout(pileOuFace, 1400);
				else WAIT_FOR_POF = true;
			}
		}
		
		override protected function swapPhase():void {
			if(RULES.SWAP) {
				var swa:SwapAnim = new SwapAnim();
				addChild(swa);
				
				if (this.HOST) {
					var randBlue:Card = bluePlayer.getRandomCard();
					var randRed:Card = redPlayer.getRandomCard();
					
					bluePlayer.swapCardWith(randBlue, randRed.id);
					redPlayer.swapCardWith(randRed, randBlue.id);
					
					SWAP_CARDS = [{ index:randBlue.index, id:randRed.id }, { index:randRed.index, id:randBlue.id } ]
					
					Socket.send('<swap toroom="' + this.ID + '">' + '<blue><index>'+randRed.index+'</index><id>'+randBlue.id+'</id></blue><red><index>'+randBlue.index+'</index><id>'+randRed.id+'</id></red>' + '</swap>');
					
					SWAP_OK = true;
					if (POF_OK) pileOuFace();
					else WAIT_FOR_POF = true;
				} else {
					if (SWAP_OK) {
						bluePlayer.swapCardWith(bluePlayer.getCardAt(SWAP_CARDS[0].index), SWAP_CARDS[0].id);
						redPlayer.swapCardWith(redPlayer.getCardAt(SWAP_CARDS[1].index), SWAP_CARDS[1].id);
						if (POF_OK) setTimeout(pileOuFace, 1400);
						else WAIT_FOR_POF = true;
					} else WAIT_FOR_SWAP = true;
				}
			} else {
				if (POF_OK) pileOuFace();
				else WAIT_FOR_POF = true;
			}
		}
		
		override protected function pileOuFace():void {
			if (!SUDDEN_DEATH_NEXT) {
				addChild(pof);
				pof.start();
				
				timeline = (pof.blueOrRed == 'blue') ? new Array("RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE") : new Array("BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED");
			}
			setTimeout(letsGetStarted, 1000);
		}
		
		override protected function tileTouched(e:Event):void {
			super.tileTouched(e)
			if (selectedCard) {
				var tile:Tile = e.target as Tile;
				Socket.send('<cardMove toroom="'+this.ID+'"><c>'+tile.card.legacyName+'</c><p>'+tile.legacyName+'</p></cardMove>');
			}
		}
		
		override protected function cardDropedOnTile(event:Event):void {
			super.cardDropedOnTile(event)
			if (event.data is Tile) {
				var tile:Tile = event.data as Tile;
				Socket.send('<cardMove toroom="'+this.ID+'"><c>'+tile.card.legacyName+'</c><p>'+tile.legacyName+'</p></cardMove>');
			}
		}
		
		private function check_leave(e:Event):void {
			// l'adversaire à peut-être quitté le match
			setTimeout(autoPlay, 5000);
		}
		
		override protected function autoPlay():void {
			var remainingCards:Array = ('RED' == timeline[turn]) ? redPlayer.getRemainingCards() : bluePlayer.getRemainingCards();
			var remainingTiles:Array = board.getRemainingTiles();
			var randCard:Card = (RULES.ORDER == tripleTriadRules.RULE_ORDER) ? remainingCards[0] : remainingCards[tools.rand(remainingCards.length - 1)];
			var randTile:Tile = remainingTiles[tools.rand(remainingTiles.length - 1)];
			randTile.card = randCard;
			var absoluteX:int = -randCard.parent.x - randCard.parent.parent.x +randTile.x + this.board.x;
			var absoluteY:int = -randCard.parent.y - randCard.parent.parent.y +randTile.y + this.board.y;
			randCard.fly(absoluteX, absoluteY);
			randCard.draggable = false;
			CORE.applyRules(randTile, timeline[turn]);
			Socket.send('<cardMove toroom="'+this.ID+'"><c>'+randCard.legacyName+'</c><p>'+randTile.legacyName+'</p></cardMove>');
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
					
					if (MGPBoon) {
						MGPReward = 48 + tools.rand(15) + Math.round(48 * 20 / 100);
						Game.PROFILE_DATAS.BOONS.MGP -= 1;
					} else MGPReward = 48 + tools.rand(15);
						
					if (XPBoon) {
						XPReward = 25 + Math.round(25 * 20 / 100);
						Game.PROFILE_DATAS.BOONS.XP -= 1;
					} else XPReward = 25;
						
					Game.PROFILE_DATAS.MGP += MGPReward;
					Game.PROFILE_DATAS.PVP_XP += XPReward;
					
					Achiev = new Achievements();
					NewAchiev = Achiev.check();
					Save.save(Game.PROFILE_DATAS);
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_DRAW'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, ID:this.ID, HOST:this.HOST, OPPONENT:RED_PLAYER_NAME, RULES:this.RULES});
				}
			} else {
				if (scores.BLUE > scores.RED) {
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
					
					if (MGPBoon) {
						MGPReward = 96 + tools.rand(30) + Math.round(96 * 20 / 100);
						Game.PROFILE_DATAS.BOONS.MGP -= 1;
					} else MGPReward = 96 + tools.rand(30);
						
					if (XPBoon) {
						XPReward = 50 + Math.round(50 * 20 / 100);
						Game.PROFILE_DATAS.BOONS.XP -= 1;
					} else XPReward = 50;
						
					Game.PROFILE_DATAS.MGP += MGPReward;
					Game.PROFILE_DATAS.PVP_XP += XPReward;
					
					Achiev = new Achievements();
					NewAchiev = Achiev.check();
					Save.save(Game.PROFILE_DATAS);
					InventoryScreen.sortBag();
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_WIN'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, ID:this.ID, HOST:this.HOST, OPPONENT:RED_PLAYER_NAME, RULES:this.RULES});
				} else {
					var rwAnim:RedWinAnim = new RedWinAnim();
					this.addChild(rwAnim);
					
					Game.PROFILE_DATAS.STATS.DEFEATS++;
					Game.PROFILE_DATAS.ENDED_MATCHES += 1;
					
						if (MGPBoon) {
							MGPReward = 24 + tools.rand(5) + Math.round(24 * 20 / 100);
							Game.PROFILE_DATAS.BOONS.MGP -= 1;
						} else MGPReward = 24 + tools.rand(5);
						
						if (XPBoon) {
							XPReward = 12 + Math.round(12 * 20 / 100);
							Game.PROFILE_DATAS.BOONS.XP -= 1;
						} else XPReward = 12;
						
						Game.PROFILE_DATAS.MGP += MGPReward;
						Game.PROFILE_DATAS.PVP_XP += XPReward;
					
					Achiev = new Achievements();
					NewAchiev = Achiev.check();
					Save.save(Game.PROFILE_DATAS);
					
					setTimeout(rematch, intervalDuration, {label:i18n.gettext('STR_YOU_LOSE'), MGP:MGPReward, XP:XPReward, items:null, achievements:NewAchiev, ID:this.ID, HOST:this.HOST, OPPONENT:RED_PLAYER_NAME, RULES:this.RULES});
				}
			}
		}
		
		override protected function suddenDeathDispatcher():void {
			var bCards:Object = bluePlayer.getCardIdsByColor();
			var rCards:Object = redPlayer.getCardIdsByColor();
			this.dispatchEventWith('sudden_death', false, { RULES:RULES, BLUE_CARDS:bCards.BLUE.concat(rCards.BLUE), RED_CARDS:bCards.RED.concat(rCards.RED), RED_PLAYER_NAME:RED_PLAYER_NAME, SUDDEN_DEATH_NEXT:true, timeline:timeline });
		}
		
	}

}