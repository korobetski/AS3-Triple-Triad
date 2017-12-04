package tto.screens 
{
	import feathers.controls.Label;
	import feathers.controls.LayoutGroup;
	import feathers.controls.ProgressBar;
	import feathers.layout.VerticalLayout;
	import flash.text.TextFormatAlign;
	import starling.animation.Tween;
	import starling.core.Starling;
	import starling.display.DisplayObjectContainer;
	import starling.display.Sprite;
	import starling.filters.BlurFilter;
	import tto.datas.tripleTriadRules;
	import tto.display.Card;
	import tto.Game;
	import tto.utils.tools;
	/**
	 * ...
	 * @author Mao
	 */
	public class playerPanel extends LayoutGroup
	{
		public static const BLUE_COLOR:String = 'BLUE';
		public static const RED_COLOR:String = 'RED';
		public static const CARD_TOUCH:String = 'CARD_TOUCH';
		public static const TIME_UP_EVENT:String = 'TIME_UP';
		
		private var playerHand:Sprite;
		private var cards:Vector.<Card>;
		private var _cardIds:Array;
		private var _player_color:String;
		private var playerNameLabel:Label;
		private var playerTimer:ProgressBar;
		private var timerProgressTween:Tween;
		private var _listenTouch:Boolean;
		private var _isReady:Boolean;
		private var _timer:uint = 30;
		
		
		public function playerPanel(player_color:String, player_name:String, cardIds:Array, _w:uint = 308) 
		{
			this.width = _w;
			
			_cardIds = cardIds;
			_player_color = player_color;
			_isReady = false;
			
			var userLayoutGroup:LayoutGroup = new LayoutGroup();
			userLayoutGroup.width = _w;
			addChild(userLayoutGroup);
			
			var VL:VerticalLayout = new VerticalLayout();
			VL.gap = 4;
			VL.padding = 8;
			VL.horizontalAlign = (_player_color == 'BLUE') ? VerticalLayout.HORIZONTAL_ALIGN_RIGHT : VerticalLayout.HORIZONTAL_ALIGN_LEFT;
			userLayoutGroup.layout = VL;
			
			playerTimer = new ProgressBar();
			playerTimer.direction = ProgressBar.DIRECTION_HORIZONTAL;
			playerTimer.minimum = 0;
			playerTimer.maximum = _timer;
			playerTimer.value = 0;
			userLayoutGroup.addChild(playerTimer);
			
			playerNameLabel = new Label();
			playerNameLabel.styleName = "tto-" + player_color.toLowerCase() + "-player";
			playerNameLabel.filter = BlurFilter.createDropShadow(0, 0, 0x000000, 0.8, 0.2, 0.5);
			playerNameLabel.text = player_name;
			playerNameLabel.x = _w - playerNameLabel.width;
			playerNameLabel.maxWidth = _w;
			userLayoutGroup.addChild(playerNameLabel);
			
			playerHand = new Sprite();
			playerHand.x = 1;
			playerHand.y = 80;
			addChild(playerHand);
			drawCards(cardIds);
		}
		
		public function drawCards(cardIds:Array):void {
			_cardIds = cardIds;
			cards = new <Card>[];
			
			tools.purge(playerHand);
			
			var cardReady:uint = 0;
			for (var i:uint = 0; i < 5; i++ ) {
				var newCard:Card = new Card();
				if (uint(_cardIds[i]) > 0) {
					newCard.index = i;
					newCard.legacyName = (_player_color == 'RED') ? ("ply_1_c_" + i) : ("ply_0_c_" + i);
					cardReady++;
					newCard.draw(String(_cardIds[i]), Game.PROFILE_DATAS.MODE);
					if (i < 3)  {
						newCard.x = 51+i*102;
						newCard.y = 64;
					} else {
						newCard.x = (i-2)*102;
						newCard.y = 64+132;
					}
					newCard.draggable = true;
					newCard.hide();
					cards.push(newCard);
					newCard.color = _player_color;
					newCard.position = [newCard.x, newCard.y];
					playerHand.addChild(newCard);
				}
			}
			
			if (cardReady == 5) _isReady = true;
		}
		
		public function getCardAt(index:uint):Card {
			if (index >= 0 && index < 5) {
				return cards[index]
			} else {
				return null
			}
		}
		
		
		public function set listenTouch(value:Boolean):void {
			_listenTouch = value;
			var remainingCards:Array = getRemainingCards();
			var card:Card
			for (var i:uint = 0, l:uint = remainingCards.length; i < l; i++ ) {
				card = remainingCards[i];
				card.useHandCursor = value;
				card.touchable = value;
				card.draggable = value;
			}
		}
		
		public function orderListenTouch():Card {
			var _cards:Array = this.getRemainingCards();
			_cards[0].useHandCursor = true;
			_cards[0].touchable = true;
			_cards[0].draggable = true;
			_cards[0].selected = true;
			return _cards[0];
		}
		
		public function chaosListenTouch():Card {
			var _cards:Array = this.getRemainingCards();
			var rand:uint = tools.rand(_cards.length - 1);
			_cards[rand].useHandCursor = true;
			_cards[rand].touchable = true;
			_cards[rand].draggable = true;
			_cards[rand].selected = true;
			return _cards[rand];
		}
		
		public function applyAscension(ascensionDatas:Object, fallenAce:Boolean = false):void {
			var card:Card
			for (var i:uint = 0; i < 5; i++ ) {
				card = cards[i];
				if (card.type && int(ascensionDatas[card.type]) !== 0) {
					card.modifier = int(ascensionDatas[card.type]);
					
					if (card.tile) {
						if (fallenAce == true) {
							card.tile.topPow = (card.topPow == 10) ? 0 : card.topPow;
							card.tile.rightPow = (card.rightPow == 10) ? 0 : card.rightPow;
							card.tile.bottomPow = (card.bottomPow == 10) ? 0 : card.bottomPow;
							card.tile.leftPow = (card.leftPow == 10) ? 0 : card.leftPow;
						} else {
							card.tile.topPow = card.topPow;
							card.tile.rightPow = card.rightPow;
							card.tile.bottomPow = card.bottomPow;
							card.tile.leftPow = card.leftPow;
						}
						card.tile.topPow = tools.madmax(card.tile.topPow + card.modifier);
						card.tile.rightPow = tools.madmax(card.tile.rightPow + card.modifier);
						card.tile.bottomPow = tools.madmax(card.tile.bottomPow + card.modifier);
						card.tile.leftPow = tools.madmax(card.tile.leftPow + card.modifier);
					}
				}
			}
		}
		
		public function set openRule(rule:String):void {
			var card:Card;
			switch (rule) {
				case tripleTriadRules.RULE_ALL_OPEN:
					for each(card in cards) card.backToFront();
					break;
				case tripleTriadRules.RULE_THREE_OPEN:
					hideAllCards()
					var randomizer:Array = [0, 1, 2, 3, 4];
					for (var i:uint = 0; i < 3; i++ ) {
						var rand:uint = randomizer.splice(tools.rand(randomizer.length-1), 1)[0];
						card = cards[rand];
						card.backToFront();
					}
					break;
				case tripleTriadRules.RULE_DEFAULT_OPEN:
					hideAllCards()
					break;
				default:
					hideAllCards()
					break;
			}
		}
		
		private function hideAllCards():void {
			var card:Card
			for (var i:uint = 0; i < 5; i++ ) {
				card = cards[i];
				card.hide();
			}
		}
		
		public function getRandomCard():Card {
			return cards[tools.rand(4)];
		}
		
		public function swapCardWith(card:Card, newId:uint):void {
			card.flyAndSwap(card.position[0], card.position[1], newId);
		}
		
		public function getRemainingCards():Array {
			var remaining:Array = [];
			var card:Card
			for (var i:uint = 0; i < 5; i++ ) {
				card = cards[i];
				if (!card.tile) remaining.push(card);
			}
			return remaining;
		}
		
		public function getScore():int {
			var score:int = 5;
			for each(var card:Card in cards) {
				if (card.color !== _player_color) score -= 1;
			}
			return score;
		}
		
		public function getScores():Object {
			var scores:Object = {BLUE:0, RED:0};
			for each(var card:Card in cards) {
				scores[card.color] += 1;
			}
			return scores;
		}
		
		public function getCardIdsByColor():Object {
			var cardIdsByColor:Object = {BLUE:[], RED:[]};
			var card:Card
			for (var i:uint = 0; i < 5; i++ ) {
				card = cards[i];
				cardIdsByColor[card.color].push(card.id);
			}
			return cardIdsByColor;
		}
		
		public function redrawCards(cardIds:Array):void {
			if (cardIds.length == 5) {
				var card:Card
				for (var i:uint = 0; i < 5; i++ ) {
					card = cards[i];
					if (uint(cardIds[i]) > 0) {
						card.draw(String(uint(cardIds[i])), Game.PROFILE_DATAS.MODE);
						card.tile = null;
						card.x = card.position[0];
						card.y = card.position[1];
					}
				}
			}
		}
		
		public function get listenTouch():Boolean {
			return _listenTouch;
		}
		
		public function get isReady():Boolean {
			return _isReady;
		}
		
		public function set isReady(value:Boolean):void {
			_isReady = value;
		}
		
		public function get timer():uint {
			return _timer;
		}
		
		public function set timer(value:uint):void {
			_timer = value;
			playerTimer.maximum = _timer;
		}
		
		
		public function setTimer():void {
			playerTimer.value = _timer;
			timerProgressTween = new Tween(playerTimer, _timer);
			timerProgressTween.animate("value", 0);
			Starling.juggler.add(timerProgressTween);
			timerProgressTween.onComplete = timeUp;
		}
		
		
		public function razTimer():void {
			playerTimer.value = 0;
			Starling.juggler.remove(timerProgressTween);
			Starling.juggler.removeTweens(playerTimer);
		}
		
		
		private function timeUp():void {
			this.dispatchEventWith(playerPanel.TIME_UP_EVENT);
		}
		
		override public function dispose():void {
			var card:Card
			for (var i:uint = 0; i < 5; i++ ) {
				card = cards[i];
				card.dispose();
			}
			tools.purge(this);
			super.dispose();
		}
		
	}

}