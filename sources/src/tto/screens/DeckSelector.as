package tto.screens {
	import com.adobe.utils.ArrayUtil;
	import feathers.controls.Button;
	import feathers.controls.Header;
	import feathers.controls.LayoutGroup;
	import feathers.controls.List;
	import feathers.controls.Panel;
	import feathers.core.IFeathersEventDispatcher;
	import feathers.data.ListCollection;
	import feathers.layout.HorizontalLayout;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.display.CardThumb;
	import tto.Game;
	import tto.utils.i18n;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class DeckSelector extends Panel implements IFeathersEventDispatcher {
		private var deckList:List;
		private var _selectedDeck:Object;
		private var chooseBtn:Button;
		private var randomBtn:Button;
		
		public function DeckSelector() {
			super();
		}
		
		override protected function draw():void {
			deckList.itemRendererProperties.horizontalAlign = 'center';
			deckList.itemRendererProperties.accessoryPosition = 'bottom';
			
			super.draw();
		}
		
		override protected function initialize():void {
			super.initialize();
			
			this.title = i18n.gettext('STR_CARD_DECKS');
			this.footerFactory = deckSelectorFooter;
			
			deckList = new List();
			deckList.x = 8;
			deckList.width = 300;
			var deckCollection:ListCollection = new ListCollection();
			var listLayout:HorizontalLayout = new HorizontalLayout();
			listLayout.horizontalAlign = HorizontalLayout.HORIZONTAL_ALIGN_CENTER;
			var cardThumbsLG:LayoutGroup
			var deck:Object;
			var fullDeck:Boolean;
			var fullChecker:uint;
			var savedDeckCardId:uint
			var cardThumb:CardThumb
			var i:uint, j:uint;
			for (i = 0; i < 5; i++) {
				cardThumbsLG = new LayoutGroup();
				if (Game.PROFILE_DATAS.DECKS[i]) {
					// if there is a deck
					deck = Game.PROFILE_DATAS.DECKS[i];
					fullDeck = false;
					fullChecker = 0;
					for (j = 0; j < 5; j++) {
						savedDeckCardId = 0;
						if (deck.cards[j])
							savedDeckCardId = deck.cards[j];
						if (savedDeckCardId > 0) {
							cardThumb = new CardThumb(savedDeckCardId, Game.PROFILE_DATAS.MODE);
							//cardThumb.enabled = false;
							cardThumbsLG.addChild(cardThumb);
							fullChecker++;
							if (fullChecker == 5)
								fullDeck = true;
						}
					}
					
					if (fullDeck)
						deckCollection.addItem({label: String(deck.name), accessory: cardThumbsLG, cards: deck.cards});
				}
				cardThumbsLG.layout = listLayout;
			}
			if (deckCollection.length == 0) {
				
			}
			deckList.dataProvider = deckCollection;
			deckList.addEventListener(Event.CHANGE, deckListController);
			addChild(deckList);
		}
		
		override public function dispose():void {
			deckList.removeEventListener(Event.CHANGE, deckListController);
			randomBtn.removeEventListener(Event.TRIGGERED, randomBtnHandler);
			chooseBtn.removeEventListener(Event.TRIGGERED, chooseBtnHandler);
			super.dispose();
		}
		
		private function deckListController(e:Event):void {
			if (deckList.selectedItem) {
				_selectedDeck = deckList.selectedItem;
				chooseBtn.isEnabled = true;
			} else {
				_selectedDeck = null;
				chooseBtn.isEnabled = false;
			}
		}
		
		private function deckSelectorFooter():Header {
			var footer:Header = new Header();
			
			randomBtn = new Button();
			randomBtn.label = i18n.gettext("RULE_RANDOM");
			randomBtn.addEventListener(Event.TRIGGERED, randomBtnHandler);
			
			chooseBtn = new Button();
			chooseBtn.isEnabled = false;
			chooseBtn.label = i18n.gettext("STR_CHOOSE_DECK");
			chooseBtn.addEventListener(Event.TRIGGERED, chooseBtnHandler);
			footer.rightItems = new <DisplayObject>[randomBtn, chooseBtn];
			
			return footer;
		}
		
		private function randomBtnHandler(e:Event):void {
			var randomizer:Array = ArrayUtil.copyArray(Game.PROFILE_DATAS.CARDS);
			var randomCards:Array = [];
			if (randomizer.length == 5) {
				randomCards = randomizer;
			} else {
				while (randomCards.length < 5) {
					var rand:uint = (randomizer.length > 1) ? (randomizer.splice(tools.rand(randomizer.length - 1), 1)[0]) : (randomizer[0]);
					randomCards.push(rand);
				}
			}
			
			this.dispatchEventWith(Event.COMPLETE, false, {cards: randomCards});
		}
		
		private function chooseBtnHandler(e:Event):void {
			this.dispatchEventWith(Event.COMPLETE, false, _selectedDeck);
		}
		
		public function get selectedDeck():Object {
			return _selectedDeck;
		}
		
		public function set selectedDeck(value:Object):void {
			_selectedDeck = value;
		}
	}

}