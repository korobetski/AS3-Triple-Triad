package tto.screens {
	import com.adobe.utils.ArrayUtil;
	import feathers.controls.GroupedList;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.LayoutGroup;
	import feathers.controls.List;
	import feathers.controls.PageIndicator;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.controls.ScrollContainer;
	import feathers.controls.TextInput;
	import feathers.data.HierarchicalCollection;
	import feathers.data.ListCollection;
	import feathers.layout.AnchorLayoutData;
	import feathers.layout.HorizontalLayout;
	import feathers.layout.TiledRowsLayout;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.events.Event;
	import starling.filters.BlurFilter;
	import tto.datas.CardItem;
	import tto.datas.cards;
	import tto.datas.Save;
	import tto.display.Card;
	import tto.display.CardThumb;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class DecksScreen extends Screen {
		private var userBar:UserBar;
		private var _userDecks:Array
		private var _deckDetails:LayoutGroup;
		private var _deckDetailsPanel:Panel;
		private var _deckList:List;
		private var _deckCollection:ListCollection;
		private var _deckListLayout:HorizontalLayout;
		private var _cardList:ScrollContainer;
		private var _cardThumbs:Vector.<CardItem>;
		private var userCards:Array;
		private var userCardsNb:uint;
		private var _pageIndicator:PageIndicator;
		private var cardTotalLabel:Label;
		private var deckDetails:LayoutGroup;
		private var deckPowerLabel:Label;
		private var _selectedDeckLabel:String;
		private var _selectedDeckIndex:uint;
		private var cardDetails:GroupedList;
		private var selectedCardId:uint;
		private var _deckName:TextInput;
		private var _selector:Image;
		private var saveDeck:feathers.controls.Button;
		private var resetDeck:feathers.controls.Button;
		private var sampleCard:Card;
		private var backButton:Button;
		
		
		public function DecksScreen() {
			super();
		}
		
		override protected function draw():void {
			_deckList.itemRendererProperties.horizontalAlign = 'center';
			_deckList.itemRendererProperties.accessoryPosition = 'bottom';
			_deckList.itemRendererProperties.gap = 0;
			
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			_cardList.x = _deckList.x + _deckList.width + 8;
			_pageIndicator.x = _cardList.x + 8;
			_pageIndicator.y = _cardList.y + _cardList.height + 16;
			cardTotalLabel.x = _cardList.x + _cardList.width - cardTotalLabel.width - 16;
			cardTotalLabel.y = _cardList.y + _cardList.height + 16;
			sampleCard.pivotX = sampleCard.pivotY = 0;
			sampleCard.x = _cardList.x;
			sampleCard.y = cardTotalLabel.y + cardTotalLabel.height + 24;
			cardDetails.x =  sampleCard.x + sampleCard.width - 24;
			cardDetails.y =  cardTotalLabel.y + cardTotalLabel.height + 24;
			cardDetails.width = _cardList.width - 114;
			_deckName.x = _cardList.x + _cardList.width + 8;
			deckDetails.x = _cardList.x + _cardList.width + 8;
			deckDetails.y = 48;
			resetDeck.x = _cardList.x + _cardList.width + 8;
			resetDeck.y = deckDetails.y + deckDetails.height +8;
			saveDeck.x = resetDeck.x + resetDeck.width + 16;
			saveDeck.y = deckDetails.y + deckDetails.height +8;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			userBar = new UserBar('CARD_DECKS');
			addChild(userBar);
			
			_userDecks = ArrayUtil.copyArray(Game.PROFILE_DATAS.DECKS);
			
			_selector = new Image(Assets.manager.getTexture('selectedCardThumb'));
			_selector.visible = false
			_selector.touchable = false;
			_selector.pivotX = _selector.pivotY = 14;
			
			_deckDetails = new LayoutGroup();
			_deckDetailsPanel = new Panel();
			_deckDetailsPanel.title = i18n.gettext('STR_CARD_DECKS');
			_deckDetailsPanel.headerFactory = deckDetailsHeader
			_deckDetailsPanel.x = 8
			_deckDetailsPanel.y = 96
			_deckDetailsPanel.width = stage.width - 16;
			_deckDetailsPanel.height = stage.height - 96 - 48;
			_deckDetails.addChild(_deckDetailsPanel)
			
			_deckList = new List()
			_deckList.width = 280;
			_deckCollection = new ListCollection();
			_deckListLayout = new HorizontalLayout();
			_deckListLayout.horizontalAlign = HorizontalLayout.HORIZONTAL_ALIGN_CENTER;
			_deckListLayout.gap = 0;
			for (var i:uint = 0; i < 5; i++) {
				var cardThumbsLG:LayoutGroup = new LayoutGroup();
				if (_userDecks[i]) {
					var deck:Object = _userDecks[i];
					var cardThumb:CardThumb;
					for (var j:uint = 0; j < 5; j++) {
						var savedDeckCardId:uint = 0;
						if (deck.cards[j])
							savedDeckCardId = deck.cards[j];
						if (savedDeckCardId) {
							cardThumb = new CardThumb(savedDeckCardId, Game.PROFILE_DATAS.MODE);
							cardThumbsLG.addChild(cardThumb);
						} else {
							cardThumb = new CardThumb(0);
							cardThumb.enabled = false;
							cardThumbsLG.addChild(cardThumb);
						}
					}
					_deckCollection.addItem({label: String(deck.name), accessory: cardThumbsLG, cards: deck.cards, deckIndex: i});
				} else {
					// empty deck
					for (j = 0; j < 5; j++) {
						cardThumb = new CardThumb(0);
						cardThumb.enabled = false;
						cardThumbsLG.addChild(cardThumb);
					}
					_deckCollection.addItem({label: i18n.gettext("STR_DECK") + ' ' + String(int(i + 1)), accessory: cardThumbsLG, cards: [0, 0, 0, 0, 0], deckIndex: i});
				}
				cardThumbsLG.layout = _deckListLayout;
			}
			_deckList.dataProvider = _deckCollection;
			_deckList.addEventListener(Event.CHANGE, deckListController);
			_deckDetailsPanel.addChild(_deckList);
			
			_cardList = new ScrollContainer();
			_cardThumbs = new <CardItem>[];
			userCards = Game.PROFILE_DATAS.CARDS.sort(Array.NUMERIC);
			userCardsNb = userCards.length;
			_cardList.snapToPages = true;
			_cardList.width = 378;
			_cardList.height = 50*5
			var loop:uint = (cards.DATAS.length - 1);
			for (i = 0; i < loop; i++) {
				var ownedCardId:uint = 0;
				if (userCards[i])
					ownedCardId = userCards[i];
				if (ownedCardId) {
					var cardBtn:CardItem = new CardItem(ownedCardId);
					var lane:int = i % 8;
					cardBtn.x = (lane * 46)
					cardBtn.y = (((i - lane) * 46) / 8);
					cardBtn.iconId = (Game.PROFILE_DATAS.MODE + 'thumb_' + String(ownedCardId));
					cardBtn.addEventListener(Event.TRIGGERED, cardTriggered);
					_cardThumbs.push(cardBtn);
					_cardList.addChild(cardBtn);
				}
			}
			/*
			var listLayout:TiledRowsLayout = new TiledRowsLayout();
			listLayout.paging = TiledRowsLayout.PAGING_HORIZONTAL;
			listLayout.useSquareTiles = true;
			listLayout.tileHorizontalAlign = TiledRowsLayout.TILE_HORIZONTAL_ALIGN_CENTER;
			listLayout.horizontalAlign = TiledRowsLayout.HORIZONTAL_ALIGN_CENTER;
			listLayout.verticalAlign = TiledRowsLayout.VERTICAL_ALIGN_TOP;
			listLayout.paddingTop = 8;
			listLayout.paddingBottom = 0;
			listLayout.paddingLeft = listLayout.paddingRight = 4;
			listLayout.gap = 0;
			_cardList.layout = listLayout;
			*/
			_cardList.horizontalScrollPolicy = ScrollContainer.SCROLL_POLICY_OFF
			_cardList.addEventListener(Event.SCROLL, list_scrollHandler);
			
			_cardList.addChild(_selector);
			_deckDetailsPanel.addChild(_cardList);
			
			cardTotalLabel = new Label();
			cardTotalLabel.text = i18n.gettext('STR_TOTAL').toUpperCase() + ' : ' + userCardsNb;
			cardTotalLabel.filter = BlurFilter.createDropShadow(0, 0, 0xFFCC33, 0.85, 0.4, 0.5);
			cardTotalLabel.styleName = Label.ALTERNATE_STYLE_NAME_HEADING;
			_deckDetailsPanel.addChild(cardTotalLabel)
			
			_pageIndicator = new PageIndicator();
			var pageIndicatorLayoutData:AnchorLayoutData = new AnchorLayoutData();
			pageIndicatorLayoutData.left = 0;
			pageIndicatorLayoutData.right = 0;
			pageIndicatorLayoutData.verticalCenter = 0;
			_pageIndicator.addEventListener(Event.CHANGE, pageIndicator_changeHandler);
			_pageIndicator.layoutData = pageIndicatorLayoutData;
			_deckDetailsPanel.addChild(_pageIndicator)
			
			sampleCard = new Card();
			_deckDetailsPanel.addChild(sampleCard);
			
			cardDetails = new GroupedList();
			cardDetails.isSelectable = false;
			_deckDetailsPanel.addChild(cardDetails);
			
			_deckName = new TextInput();
			_deckName.isEnabled = false;
			_deckDetailsPanel.addChild(_deckName);
			
			deckDetails = new LayoutGroup();
			for (i = 0; i < 5; i++) {
				var deckCard:Card = new Card();
				deckCard.y = 64 + ((i - i % 3) * 132) / 3;
				deckCard.x = (i < 3) ? (52 + i % 3 * 108) : (104 + i % 3 * 108);
				deckCard.touchable = true;
				deckCard.draggable = false;
				deckDetails.addChild(deckCard);
			}
			deckDetails.addEventListener(Event.TRIGGERED, deckCard_Touched);
			
			deckPowerLabel = new Label();
			deckPowerLabel.text = i18n.gettext("STR_DECK_POWER") + " : 0";
			deckPowerLabel.y = 296;
			deckDetails.addChild(deckPowerLabel);
			
			_deckDetailsPanel.addChild(deckDetails);
			
			resetDeck = new feathers.controls.Button();
			resetDeck.label = i18n.gettext("STR_RESET_DECK");
			resetDeck.addEventListener(Event.TRIGGERED, resetDeckHandler);
			_deckDetailsPanel.addChild(resetDeck);
			
			saveDeck = new feathers.controls.Button();
			saveDeck.label = i18n.gettext("STR_SAVE");
			saveDeck.addEventListener(Event.TRIGGERED, saveDeck_Handler);
			_deckDetailsPanel.addChild(saveDeck);
			
			_deckDetailsPanel.horizontalScrollPolicy = Panel.SCROLL_POLICY_OFF
			_deckDetailsPanel.verticalScrollPolicy = Panel.SCROLL_POLICY_OFF
			
			this.addChild(_deckDetailsPanel);
		}
		
		private function deckCard_Touched(e:Event):void {
			if (_deckList.selectedIndex !== -1 && selectedCardId) {
				var deckCard:Card = e.target as Card;
				// if the card is already in the deck, so we swap
				for (var i:uint = 0; i < 5; i++ ) {
					var cardClip:Card = deckDetails.getChildAt(i) as Card;
					if (int(cardClip.id) == int(selectedCardId)) {
						if (int(deckCard.id) > 0) {
							cardClip.draw(String(deckCard.id), Game.PROFILE_DATAS.MODE);
						} else {
							cardClip.draw('back');
						}
					}
				}
				deckCard.draw(String(selectedCardId), Game.PROFILE_DATAS.MODE);
				updateDeckPower();
			}
		}
		
		private function cardTriggered(e:Event):void {
			// data = cardId
			if (_deckList.selectedIndex !== -1) {
				var cardItem:CardItem = (e.currentTarget as CardItem)
				var cardId:uint = cardItem.cardId;
				sampleCard.draw(String(cardId), Game.PROFILE_DATAS.MODE)
				_selector.visible = true;
				_selector.x = cardItem.x
				_selector.y = cardItem.y
				selectedCardId = cardId
				var cardDatas:Object = cards.DATAS[int(cardId)];
				var sidesLabel:Label = new Label();
				sidesLabel.styleName = "tto-blue-player";
				sidesLabel.text = cardDatas.power[0] + ' ' + cardDatas.power[1] + ' ' + cardDatas.power[2] + ' ' + cardDatas.power[3];
				cardDetails.dataProvider = new HierarchicalCollection(
				[
					{
						header: i18n.gettext("STR_" + String(Game.PROFILE_DATAS.MODE).toUpperCase() + "CARD_" + String(cardId)),
						children:
						[
							{ label: i18n.gettext("STR_SIDES")+" :", accessory: sidesLabel },
							{ label: i18n.gettext("STR_RARITY")+" :", accessory: new Image(Assets.manager.getTexture(String(cardDatas.rarity+'stars'))) },
							{ label: i18n.gettext("STR_CARD_TYPE")+" :", accessory: (cardDatas.type) ? new Image(Assets.manager.getTexture(String('type-'+cardDatas.type))) : null },
						]
					},
				]);
			}
		}
		
		private function deckListController(e:Event):void {
			if (_deckList.selectedItem) {
				var datas:Object = _deckList.selectedItem;
				this._selectedDeckLabel = String(datas.label);
				this._selectedDeckIndex = uint(_deckList.selectedIndex);
				_deckName.text = String(datas.label)
				_deckName.isEnabled = true;
				for (var i:uint = 0; i < 5; i++) {
					var cardClip:Card = deckDetails.getChildAt(i) as Card;
					if (datas.cards && datas.cards[i] && int(datas.cards[i]) > 0) {
						var cardId:uint = datas.cards[i];
						cardClip.draw(String(cardId), Game.PROFILE_DATAS.MODE);
					} else {
						cardClip.draw('back');
					}
				}
				updateDeckPower();
			}
		}
		
		private function updateDeckPower():void {
			var deckPower:uint = 0;
			for (var i:uint = 0; i < 5; i++) {
				var cardClip:Card = deckDetails.getChildAt(i) as Card;
				deckPower += (cardClip.data) ? cardClip.data.rarity : 0;
			}
			deckPowerLabel.text = i18n.gettext("STR_DECK_POWER") + " : " + String(deckPower);
		}
		
		
		private function resetDeckHandler(e:Event):void 
		{
			var deck:Object = (Game.PROFILE_DATAS.DECKS[_selectedDeckIndex]) ? Game.PROFILE_DATAS.DECKS[_selectedDeckIndex] : {name:i18n.gettext("STR_NEW_DECK"), cards:[]};
			var cardThumbsLG:LayoutGroup = new LayoutGroup();
			cardThumbsLG.layout = _deckListLayout;
			for (var j:uint = 0; j < 5; j++ ) {
				var cardClip:Card = deckDetails.getChildAt(j) as Card;
				cardClip.hide();
				
				var voidCard:CardThumb = new CardThumb(0);
				voidCard.enabled = false;
				cardThumbsLG.addChild(voidCard);
				deck.cards.push(0);
			}
			//Game.PROFILE_DATAS.DECKS[_selectedDeckIndex] = deck;
			Game.PROFILE_DATAS.DECKS.slice(_selectedDeckIndex, 1);
			Save.save(Game.PROFILE_DATAS);
			
			_deckCollection.setItemAt( { label:i18n.gettext("STR_DECK") + ' ' + String(int(_selectedDeckIndex + 1)), accessory:cardThumbsLG, cards:[] }, _deckList.selectedIndex);
			_deckList.selectedIndex = _selectedDeckIndex;
		}
		
		private function saveDeck_Handler(e:Event):void 
		{
			var deck:Object = (Game.PROFILE_DATAS.DECKS[_selectedDeckIndex]) ? Game.PROFILE_DATAS.DECKS[_selectedDeckIndex] : {name:i18n.gettext("STR_NEW_DECK"), cards:[]};
			var cardThumbsLG:LayoutGroup = new LayoutGroup();
			cardThumbsLG.layout = _deckListLayout;
			deck.cards = [];
			deck.name = _deckName.text;
			for (var j:uint = 0; j < 5; j++ ) {
				var cardClip:Card = deckDetails.getChildAt(j) as Card;
				if (int(cardClip.id) > 0) {
					var cardThumb:CardThumb = new CardThumb(int(cardClip.id), Game.PROFILE_DATAS.MODE);
					//cardThumb.enabled = false;
					cardThumbsLG.addChild(cardThumb);
					deck.cards.push(int(cardClip.id));
				} else {
					var voidCard:CardThumb = new CardThumb(0);
					voidCard.enabled = false;
					cardThumbsLG.addChild(voidCard);
					deck.cards.push(0);
				}
			}
			Game.PROFILE_DATAS.DECKS[_selectedDeckIndex] = deck;
			Save.save(Game.PROFILE_DATAS);
			
			_deckCollection.setItemAt( { label:String(deck.name), accessory:cardThumbsLG, cards:deck.cards }, _deckList.selectedIndex);
			_deckList.selectedIndex = _selectedDeckIndex;
		}
		
		private function list_scrollHandler(e:Event):void {
			_pageIndicator.pageCount = _cardList.horizontalPageCount;
			_pageIndicator.selectedIndex = _cardList.horizontalPageIndex;
		}
		
		private function pageIndicator_changeHandler(e:Event):void {
			_cardList.scrollToPageIndex(_pageIndicator.selectedIndex, 0, _cardList.pageThrowDuration);
		}
		
		private function deckDetailsHeader():Header {
			var header:Header = new Header();
			backButton = new Button(Assets.manager.getTexture('action_back_up'));
			//backButton.y = stage.height - backButton.height;
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			header.leftItems = new <DisplayObject>[backButton];
			return header;
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		override public function dispose():void {
			_deckList.removeEventListener(Event.CHANGE, deckListController);
			deckDetails.removeEventListener(Event.TRIGGERED, deckCard_Touched);
			resetDeck.removeEventListener(Event.TRIGGERED, resetDeckHandler);
			saveDeck.removeEventListener(Event.TRIGGERED, saveDeck_Handler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			super.dispose();
		}
	}

}