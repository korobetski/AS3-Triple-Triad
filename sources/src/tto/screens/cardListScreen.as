package tto.screens {
	import com.adobe.utils.ArrayUtil;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.LayoutGroup;
	import feathers.controls.PageIndicator;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.controls.ScrollContainer;
	import feathers.controls.text.TextFieldTextRenderer;
	import feathers.layout.TiledRowsLayout;
	import flash.text.TextFormat;
	import flash.text.TextFormatAlign;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.display.Quad;
	import starling.events.Event;
	import starling.filters.BlurFilter;
	import starling.text.TextField;
	import starling.text.TextFieldAutoSize;
	import tto.datas.cards;
	import tto.display.Card;
	import tto.display.CardThumb;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.conf;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class cardListScreen extends Screen {
		
		[Embed(source = "../../../assets/fonts/Raleway/Raleway-Regular.ttf", fontFamily = "Raleway", fontWeight = "normal", mimeType = "application/x-font", embedAsCFF = "false")] protected static const RALEWAY_REGULAR:Class;
		[Embed(source = "../../../assets/fonts/Raleway/Raleway-Italic.ttf", fontFamily = "Raleway", fontStyle="italic", fontWeight = "normal", mimeType = "application/x-font", embedAsCFF = "false")] protected static const RALEWAY_ITALIC:Class;
		
		private var cardList:Panel;
		private var sampleCard:Card;
		private var _pageIndicator:PageIndicator;
		private var cardDetailsPanel:Panel;
		private var cardTitle:Label;
		private var cardDescription:*;
		private var separator:Quad;
		private var backButton:Button;
		private var userBar:UserBar;
		private var selectedCard:CardThumb;
		private var userCards:Array;
		private var userCardsNb:uint;
		private var tiledView:LayoutGroup;
		private var _selector:Image;
		
		public function cardListScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			cardList.width = 11 * 48;
			cardList.height = stage.height - (96 + 48);
			
			tiledView.width = cardList.width - 8;
			//tiledView.height = 11 * 50;
			
			cardDetailsPanel.x = cardList.x + cardList.width + 8;
			cardDetailsPanel.width = stage.width - cardDetailsPanel.x - 8;
			cardDetailsPanel.height = stage.height - 206;
			cardDescription.width = cardDetailsPanel.width - 32 - 104;
			//separator.y = cardDescription.y + cardDescription.height + 8;
			cardList.headerFactory = headerFactory;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			userBar = new UserBar('CARD_LIST');
			addChild(userBar);
			
			cardList = new Panel();
			cardList.x = 8;
			cardList.y = 96;
			cardList.verticalScrollPolicy = ScrollContainer.SCROLL_POLICY_ON
			cardList.horizontalScrollPolicy = ScrollContainer.SCROLL_POLICY_OFF
			
			cardList.title = i18n.gettext('STR_CARD_LIST');
			userCards = Game.PROFILE_DATAS.CARDS.sort(Array.NUMERIC);
			userCardsNb = userCards.length;
			cardList.footerFactory = cardListFooterFactory;
			
			addChild(cardList);
			tiledView = new LayoutGroup()
			
			var cardId:uint;
			var cardBtn:CardThumb;
			for (var i:uint = 0, loop:uint = (cards.DATAS.length - 1); i < loop; i++) {
				cardId = uint(i + 1);
				cardBtn = new CardThumb(cardId, Game.PROFILE_DATAS.MODE);
				cardBtn.enabled = (ArrayUtil.arrayContainsValue(userCards, cardId)) ? true : false;
				tiledView.addChild(cardBtn);
			}
			tiledView.flatten()
			tiledView.addEventListener(Event.TRIGGERED, cardTriggered);
			
			var listLayout:TiledRowsLayout = new TiledRowsLayout();
			listLayout.paging = TiledRowsLayout.PAGING_HORIZONTAL;
			listLayout.useSquareTiles = true;
			listLayout.tileHorizontalAlign = TiledRowsLayout.TILE_HORIZONTAL_ALIGN_CENTER;
			listLayout.horizontalAlign = TiledRowsLayout.HORIZONTAL_ALIGN_CENTER;
			listLayout.verticalAlign = TiledRowsLayout.VERTICAL_ALIGN_TOP;
			listLayout.paddingTop = 8;
			listLayout.paddingBottom = 0;
			listLayout.paddingLeft = listLayout.paddingRight = 4;
			listLayout.gap = 2;
			tiledView.layout = listLayout;
			cardList.addChild(tiledView);
			
			_selector = new Image(Assets.manager.getTexture('selectedCardThumb'));
			_selector.visible = false
			_selector.touchable = false;
			_selector.pivotX = _selector.pivotY = 14;
			cardList.addChild(_selector);
			
			cardDetailsPanel = new Panel();
			cardDetailsPanel.padding = 16;
			cardDetailsPanel.title = i18n.gettext('STR_CARD_INFOS');
			cardDetailsPanel.y = 96;
			addChild(cardDetailsPanel);
			
			sampleCard = new Card();
			sampleCard.x = 8 + 52;
			sampleCard.y = 16 + 64;
			cardDetailsPanel.addChild(sampleCard);
			
			cardTitle = new Label();
			cardTitle.styleName = Label.ALTERNATE_STYLE_NAME_HEADING;
			cardTitle.x = 120;
			cardTitle.y = 16;
			cardDetailsPanel.addChild(cardTitle);
			
			if (conf.DATAS.language == 'ja_JA') {
				cardDescription = new TextField(246, 300, '', 'noto-ja_0', 16, 0xeeeeee);
				cardDescription.x = 120;
				cardDescription.y = 54;
				cardDescription.autoSize = TextFieldAutoSize.VERTICAL;
				//cardDescription.textFormat = descFormatJA;
				//cardDescription.height = 300;
				cardDetailsPanel.addChild(cardDescription);
			} else {
				var descFormat:TextFormat = new TextFormat('Raleway', 14, 0xeeeeee);
				descFormat.align = TextFormatAlign.JUSTIFY;
				descFormat.rightMargin = 4
				cardDescription = new TextFieldTextRenderer();
				cardDescription.x = 120;
				cardDescription.y = 54;
				cardDescription.textFormat = descFormat;
				cardDescription.embedFonts = true;
				cardDescription.width = 246;
				//cardDescription.height = 300;
				cardDescription.wordWrap = true;
				cardDescription.isHTML = true;
				cardDescription.border = true;
				cardDescription.borderColor = 0xff0000;
				cardDetailsPanel.addChild(cardDescription);
			}
		
		/*
		   separator = new Quad(380, 2, 0x999999);
		   separator.x = 120;
		   separator.visible = false;
		   cardDetailsPanel.addChild(separator);
		 */
		}
		
		private function cardListFooterFactory():Header {
			var header:Header = new Header();
			//header.styleName = "card-panel-footer";
			
			var cardTotalLabel:Label = new Label();
			cardTotalLabel.text = i18n.gettext('STR_TOTAL').toUpperCase() + ' : ' + userCardsNb;
			cardTotalLabel.filter = BlurFilter.createDropShadow(0, 0, 0xFFCC33, 0.85, 0.4, 0.5);
			cardTotalLabel.styleName = Label.ALTERNATE_STYLE_NAME_HEADING;
			header.rightItems = new <DisplayObject>[cardTotalLabel];
			return header;
		}
		
		private function cardTriggered(e:Event):void {
			var cardBtn:CardThumb = e.target as CardThumb;
			if (selectedCard)
				selectedCard.isSelected = false;
			cardBtn.isSelected = true;
			selectedCard = cardBtn;
			
			_selector.visible = true;
			_selector.x = tiledView.x + cardBtn.x
			_selector.y = tiledView.y + cardBtn.y
			
			sampleCard.draw(String(cardBtn.id), Game.PROFILE_DATAS.MODE);
			cardTitle.text = (Game.PROFILE_DATAS.ADMIN) ? "#" + cardBtn.id + ' ' + i18n.gettext("STR_" + String(Game.PROFILE_DATAS.MODE).toUpperCase() + "CARD_" + cardBtn.id) : i18n.gettext("STR_" + String(Game.PROFILE_DATAS.MODE).toUpperCase() + "CARD_" + cardBtn.id);
			cardDescription.text = i18n.gettext("STR_" + String(Game.PROFILE_DATAS.MODE).toUpperCase() + "CARD_" + cardBtn.id + "_DESC");
		/*
		 * separator.visible = true;
		 * separator.y = cardDescription.y + cardDescription.height + 8;
		 */
		}
		
		public function get selectedCardId():uint {
			return (selectedCard) ? uint(selectedCard.id) : null;
		}
		
		private function headerFactory():Header {
			var header:Header = new Header();
			
			backButton = new Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.y = stage.height - backButton.height;
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			header.leftItems = new <DisplayObject>[backButton];
			return header;
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		override public function dispose():void {
			tiledView.removeEventListener(Event.TRIGGERED, cardTriggered);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			super.dispose();
		}
	}

}