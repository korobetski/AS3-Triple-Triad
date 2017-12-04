package tto.screens {
	import com.adobe.utils.ArrayUtil;
	import feathers.controls.Screen;
	import feathers.dragDrop.DragDropManager;
	import flash.utils.setTimeout;
	import starling.display.Image;
	import starling.events.Event;
	import tto.anims.*;
	import tto.controls.cardScore;
	import tto.datas.Achievements;
	import tto.datas.tripleTriadRules;
	import tto.display.Card;
	import tto.display.Tile;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	import tto.utils.TTOCore;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class BaseMatchScreen extends Screen {
		
		public var bluePlayer:playerPanel;
		public var redPlayer:playerPanel;
		public var board:Board;
		protected var deckSelector:DeckSelector;
		
		public var timeline:Array;
		protected var turn:int;
		protected var intervalDuration:Number = 2000;
		
		protected var boardScores:cardScore;
		protected var ascensionByType:Object;
		protected var selectedCard:Card;
		protected var pof:PileOuFace;
		protected var boardRules:RulesDigest;
		
		public var RULES:Object;
		public var CORE:TTOCore;
		public var BLUE_PLAYER_NAME:String;
		public var BLUE_CARDS:Array;
		public var RED_PLAYER_NAME:String;
		public var RED_CARDS:Array;
		public var SUDDEN_DEATH_NEXT:Boolean = false;
		
		public function BaseMatchScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
		}
		
		override protected function initialize():void {
			super.initialize();
			
			boardScores = new cardScore();
			boardScores.x = 48;
			boardScores.y = 150;
			
			if (RULES.ROULETTE) {
				RULES = tripleTriadRules.roulette(Game.PROFILE_DATAS.MODE, RULES);
			}
			
			CORE = new TTOCore();
			CORE.RULES = this.RULES;
			CORE.SCREEN = this;
			
			boardRules = new RulesDigest(this.RULES);
			boardRules.x = stage.width-250-32;
			boardRules.y = 128;
			
			board = new Board();
			board.x = (1200-136*3)/2;
			board.y = 166
			board.addEventListener(Tile.CARD_DROPED_ON_TILE_EVENT, cardDropedOnTile);
			board.addEventListener(Event.TRIGGERED, tileTouched);
			
			var boardCorner:Image = new Image(Assets.manager.getTexture('boardCorner'));
			boardCorner.x = 600;
			boardCorner.y = 166 + 204;
			boardCorner.pivotX = 259;
			boardCorner.pivotY = 280;
			boardCorner.touchable = false; 
			
			if (!BLUE_PLAYER_NAME) BLUE_PLAYER_NAME = Game.PROFILE_DATAS.USERNAME;
			bluePlayer = new playerPanel(playerPanel.BLUE_COLOR, BLUE_PLAYER_NAME, BLUE_CARDS, 308);
			bluePlayer.x = 32;
			bluePlayer.y = 240;
			bluePlayer.addEventListener(playerPanel.TIME_UP_EVENT, timeUp_play);
			bluePlayer.addEventListener(Event.TRIGGERED, cardTouched);
			
			if (!RED_PLAYER_NAME) RED_PLAYER_NAME = 'NPC';
			redPlayer = new playerPanel(playerPanel.RED_COLOR, RED_PLAYER_NAME, RED_CARDS, 308);
			redPlayer.x = board.x + board.width +32;
			redPlayer.y = 240;
			
			addChild(boardScores);
			addChild(boardRules);
			addChild(board);
			addChild(boardCorner);
			addChild(bluePlayer);
			addChild(redPlayer);
			
			ascensionByType = { beast:0, garlean:0, primals:0, scions:0 };
			pof = new PileOuFace();
			deckSelectionPhase();
		}
		
		public function deckSelectionPhase():void {
			if (!SoundManager._isPlaying) SoundManager.shuffleLoop();
			
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
					setTimeout(openPhase, 1400);
				} else {
					deckSelector = new DeckSelector();
					deckSelector.addEventListener(Event.COMPLETE, deckSelected);
					addChild(deckSelector);
					deckSelector.x = board.x+16;
					deckSelector.y = board.y-32;
				}
			}
		}
		
		protected function deckSelected(e:Event, deck:Object):void {
			BLUE_CARDS = deck.cards;
			bluePlayer.drawCards(BLUE_CARDS);
			
			this.removeChild(deckSelector);
			
			openPhase();
		}
		
		protected function openPhase():void {
			SoundManager.playSound('se_ttriad.scd_2', true);
			if (RULES.TYPE_RULE == tripleTriadRules.RULE_ELEMENTAL) {
				// on dispose des éléments si la règle est effective
				board.elements();
			}
			if (String(RULES.OPEN_RULE) == tripleTriadRules.RULE_ALL_OPEN || String(RULES.OPEN_RULE) == tripleTriadRules.RULE_THREE_OPEN) {
				if (String(RULES.OPEN_RULE) == tripleTriadRules.RULE_ALL_OPEN) {
					var aoa:AllOpenAnim = new AllOpenAnim();
					this.addChild(aoa);
				}
				if (String(RULES.OPEN_RULE) == tripleTriadRules.RULE_THREE_OPEN) {
					var toa:ThreeOpenAnim = new ThreeOpenAnim();
					this.addChild(toa);
				}
				
				bluePlayer.openRule = tripleTriadRules.RULE_ALL_OPEN;
				redPlayer.openRule = String(RULES.OPEN_RULE);
			} else {
				bluePlayer.openRule = tripleTriadRules.RULE_ALL_OPEN;
				redPlayer.openRule = tripleTriadRules.RULE_DEFAULT_OPEN;
			}
			
			setTimeout(orderPhase, 1600);
		}
		
		protected function orderPhase():void {
			if (RULES.ORDER !== tripleTriadRules.RULE_DEFAULT_ORDER) {
				if (RULES.ORDER == tripleTriadRules.RULE_ORDER) {
					var orderAnim:OrderAnim = new OrderAnim();
					this.addChild(orderAnim);
				} else if (RULES.ORDER == tripleTriadRules.RULE_CHAOS) {
					var chaosAnim:ChaosAnim = new ChaosAnim();
					this.addChild(chaosAnim);
				}
				
				setTimeout(reversePhase, 1400);
			} else {
				reversePhase();
			}
		}
		
		protected function reversePhase():void {
			if (RULES.REVERSE) {
				var anim:ReverseAnim = new ReverseAnim();
				this.addChild(anim);
				
				setTimeout(fallenAcePhase, 1400);
			} else {
				fallenAcePhase();
			}
		}
		
		protected function fallenAcePhase():void {
			if (RULES.FALLEN_ACE) {
				var anim:FallenAceAnim = new FallenAceAnim();
				this.addChild(anim);
				
				setTimeout(swapPhase, 1400);
			} else {
				swapPhase();
			}
		}
		
		protected function swapPhase():void {
			if(RULES.SWAP) {
				var swa:SwapAnim = new SwapAnim();
				addChild(swa);
			
				var randBlue:Card = bluePlayer.getRandomCard();
				var randRed:Card = redPlayer.getRandomCard();
				
				bluePlayer.swapCardWith(randBlue, randRed.id);
				redPlayer.swapCardWith(randRed, randBlue.id);
				
				setTimeout(pileOuFace, 1400);
			} else {
				pileOuFace();
			}
		}
		
		protected function pileOuFace():void {
			if (!SUDDEN_DEATH_NEXT) {
				addChild(pof);
				pof.start();
				
				timeline = (pof.blueOrRed == 'blue') ? new Array("RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE") : new Array("BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED", "BLUE", "RED");
			}
			setTimeout(letsGetStarted, 1000);
		}
		
		protected function letsGetStarted():void {
			if (pof) this.removeChild(pof);
			
			var startAnim:StartAnim = new StartAnim();
			addChild(startAnim);
			
			setTimeout(nextTurn, 1400);
		}
		
		protected function cardTouched(e:Event):void {
			unselect()
			var card:Card = e.target as Card;
			if (card.touchable && card.draggable) {
				selectedCard = card;
				selectedCard.selected = true;
			}
		}
		
		protected function tileTouched(e:Event):void {
			if (selectedCard) {
				redPlayer.razTimer();
				bluePlayer.razTimer();
				var tile:Tile = e.target as Tile;
				tile.card = selectedCard;
				
				var absoluteX:int = -selectedCard.parent.x - selectedCard.parent.parent.x +tile.x + this.board.x;
				var absoluteY:int = -selectedCard.parent.y - selectedCard.parent.parent.y +tile.y + this.board.y;
				selectedCard.fly(absoluteX, absoluteY);
				tile.card.draggable = tile.card.touchable = false;
				CORE.applyRules(tile, timeline[turn]);
				unselect()
			}
		}
		
		protected function cardDropedOnTile(event:Event):void {
			redPlayer.razTimer();
			bluePlayer.razTimer();
			
			if (event.data is Tile) {
				var tile:Tile = event.data as Tile;
				tile.card.draggable = tile.card.touchable = false;
				CORE.applyRules(tile, timeline[turn]);
				unselect()
			}
		}
		
		// called from Socket
		public function cardOnTile(card:Card, tile:Tile):void {
			redPlayer.razTimer();
			bluePlayer.razTimer();
			
			tile.card = card;
			
			var absoluteX:int = -card.parent.x - card.parent.parent.x +tile.x + this.board.x;
			var absoluteY:int = -card.parent.y - card.parent.parent.y +tile.y + this.board.y;
			card.fly(absoluteX, absoluteY);
			card.draggable = false;
			this.CORE.applyRules(tile, timeline[turn]);
			unselect();
		}
		
		protected function unselect():void {
			if (selectedCard) {
				selectedCard.selected = false;
				selectedCard = null;
			}
		}
		
		public function updateScores():Object {
			var bScores:Object = bluePlayer.getScores();
			var rScores:Object = redPlayer.getScores();
			var globalScores:Object = { BLUE:(bScores.BLUE + rScores.BLUE), RED:(bScores.RED + rScores.RED) };
			boardScores.scores = globalScores;
			return globalScores;
		}
		
		protected function timeUp_play(e:Event):void {
			bluePlayer.listenTouch = false;
			DragDropManager.cancelDrag();
			autoPlay();
		}
		
		
		public function ascensionPhase(tile:Tile):void {
			//trace('ascensionPhase : '+tile.id)
			// la phase d'ascension se situe normalement juste avant le changement de tour
			var ascend:Boolean = false;
			if (RULES.TYPE_RULE == tripleTriadRules.RULE_ASCENSION || RULES.TYPE_RULE == tripleTriadRules.RULE_DESCENSION) {
				if (RULES.TYPE_RULE == tripleTriadRules.RULE_ASCENSION) {
					if (tile.card.type) {
						var asa:AscensionAnim = new AscensionAnim();
						addChild(asa);
						ascensionByType[tile.card.type] += 1;
						ascend = true;
					}
				} else if (RULES.TYPE_RULE == tripleTriadRules.RULE_DESCENSION) {
					if (tile.card.type) {
						var dsa:DescensionAnim = new DescensionAnim();
						addChild(dsa);
						ascensionByType[tile.card.type] -= 1;
						ascend = true;
					}
				}
				// ascensionByType = {beast:0, garlean:0, primals:0, scions:0 }
				// pour chaque carte typée posée sur le plateau, toute les cartes de ce type incrementent un modifier
				// trace('beast : '+ascensionByType.beast, 'garlean : '+ascensionByType.garlean, 'primals : '+ascensionByType.primals, 'scions : '+ascensionByType.scions)
				
				bluePlayer.applyAscension(ascensionByType, Boolean(RULES.FALLEN_ACE));
				redPlayer.applyAscension(ascensionByType, Boolean(RULES.FALLEN_ACE));
			}
			if (ascend) {
				setTimeout(nextTurn, 1200);
			} else {
				nextTurn();
			}
		}
		
		protected function nextTurn():void {
			selectedCard = null;
			updateScores();
			
			turn++;
			
			if (turn == 10) {
				endGame();
			} else {
				if ('RED' == timeline[turn]) {
				SoundManager.playSound('se_ttriad.scd_4', true);
					var redAnim:RedTurnAnim = new RedTurnAnim();
					addChild(redAnim);
					bluePlayer.razTimer();
					redPlayer.setTimer();
					bluePlayer.listenTouch = false;
					opponentPhase()
				} else {
					SoundManager.playSound('se_ttriad.scd_4', true);
					var blueAnim:BlueTurnAnim = new BlueTurnAnim();
					addChild(blueAnim);
					
					redPlayer.razTimer();
					bluePlayer.setTimer();
					if (RULES.ORDER == tripleTriadRules.RULE_DEFAULT_ORDER) {
						bluePlayer.listenTouch = true;
					} else if (RULES.ORDER == tripleTriadRules.RULE_ORDER) {
						selectedCard = bluePlayer.orderListenTouch();
					} else if (RULES.ORDER == tripleTriadRules.RULE_CHAOS) {
						selectedCard = bluePlayer.chaosListenTouch();
					}
				}
			}
		}
		
		protected function opponentPhase():void {
			//setTimeout(AI, intervalDuration);
		}
		
		protected function endGame():void {
			
			// need an override
		}
		
		protected function rematch(params:Object):void {
			var rematchPanel:RematchPanel = new RematchPanel(params);
			rematchPanel.x = board.x+16;
			rematchPanel.y = board.y+16;
			addChild(rematchPanel)
		}
		
		protected function suddenDeathDispatcher():void {
			var bCards:Object = bluePlayer.getCardIdsByColor();
			var rCards:Object = redPlayer.getCardIdsByColor();
			//this.dispatchEventWith('sudden_death', false, { RULES:RULES, BLUE_CARDS:bCards.BLUE.concat(rCards.BLUE), RED_CARDS:bCards.RED.concat(rCards.RED), RED_PLAYER_NAME:i18n.gettext(this._NPC.name), _NPC:this._NPC, SUDDEN_DEATH_NEXT:true, timeline:timeline });
			// need an override
		}
		
		protected function autoPlay():void {
			var remainingCards:Array = ('RED' == timeline[turn]) ? redPlayer.getRemainingCards() : bluePlayer.getRemainingCards();
			var remainingTiles:Array = board.getRemainingTiles();
			
			// RANDOM MODE
			var randCard:Card = (RULES.ORDER == tripleTriadRules.RULE_ORDER) ? remainingCards[0] : remainingCards[tools.rand(remainingCards.length - 1)];
			var randTile:Tile = remainingTiles[tools.rand(remainingTiles.length - 1)];
			
			randTile.card = randCard;
			
			var absoluteX:int = -randCard.parent.x - randCard.parent.parent.x +randTile.x + this.board.x;
			var absoluteY:int = -randCard.parent.y - randCard.parent.parent.y +randTile.y + this.board.y;
			randCard.fly(absoluteX, absoluteY);
			randCard.draggable = false;
			CORE.applyRules(randTile, timeline[turn]);
		}
		
		override public function dispose():void {
			boardScores.dispose();
			boardRules.dispose();
			board.dispose();
			redPlayer.dispose();
			bluePlayer.dispose();
			super.dispose();
		}
	}
}