package tto.screens {
	import com.adobe.utils.ArrayUtil;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.PageIndicator;
	import feathers.controls.Panel;
	import feathers.layout.AnchorLayoutData;
	import feathers.layout.TiledRowsLayout;
	import starling.display.DisplayObject;
	import starling.display.Quad;
	import starling.events.Event;
	import starling.filters.BlurFilter;
	import tto.datas.cards;
	import tto.display.CardThumb;
	import tto.Game;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class cardPanel extends Panel {
		
		public static const CARD_SELECTED_EVENT:String = 'cardPanel_cardSelected';
		
		private var _pageIndicator:PageIndicator;
		private var _children:Vector.<CardThumb>;
		private var selectedCard:CardThumb;
		private var userCards:Array;
		private var userCardsNb:uint;
		private var _viewAll:Boolean;
		
		public function cardPanel(viewAll:Boolean = false) {
			super();
			
			//this.title = i18n.gettext('STR_CARD_LIST');
			_children = new <CardThumb>[];
			userCards = Game.PROFILE_DATAS.CARDS.sort(Array.NUMERIC);
			userCardsNb = userCards.length;
			
			this.header = null;
			this.footerFactory = thisFooterFactory;
			this.snapToPages = true;
			this.width = 320;
			this.height = 350;
			
			var loop:uint = (viewAll) ? (cards.DATAS.length - 1) : userCardsNb;
			for (var i:uint = 0; i < loop; i++ ) {
				var cardBtn:CardThumb;
				if (viewAll) {
					var cardId:uint = uint(i + 1);
					cardBtn = new CardThumb(cardId, Game.PROFILE_DATAS.MODE);
					cardBtn.name = String(cardId);
					cardBtn.enabled = (ArrayUtil.arrayContainsValue(userCards, cardId)) ? true : false;
					cardBtn.addEventListener(Event.TRIGGERED, cardTriggered);
					_children.push(cardBtn);
					this.addChild(cardBtn);	
				} else {
					var ownedCardId:uint = 0; 
					if (userCards[i]) ownedCardId = userCards[i];
					if (ownedCardId) {
						cardBtn = new CardThumb(ownedCardId, Game.PROFILE_DATAS.MODE);
						cardBtn.name = String(ownedCardId);
						cardBtn.addEventListener(Event.TRIGGERED, cardTriggered);
						_children.push(cardBtn);
						this.addChild(cardBtn);	
					}
				}
			}
			
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
			this.layout = listLayout;
			this.addEventListener(Event.SCROLL, list_scrollHandler);
			
		}
		
		override protected function draw():void 
		{
			super.draw();
			
		}
		
		private function cardTriggered(e:Event):void 
		{
			var cardBtn:CardThumb = e.currentTarget as CardThumb;
			if (selectedCard) selectedCard.isSelected = false;
			cardBtn.isSelected = true;
			selectedCard = cardBtn;
			this.dispatchEventWith(cardPanel.CARD_SELECTED_EVENT, false, cardBtn.name);
		}
		
		private function thisFooterFactory():Header {
			var header:Header = new Header();
			//header.styleName = "card-panel-footer";
			
			var cardTotalLabel:Label = new Label();
			cardTotalLabel.text = i18n.gettext('STR_TOTAL').toUpperCase() + ' : ' + userCardsNb;
			cardTotalLabel.filter = BlurFilter.createDropShadow(0, 0, 0xFFCC33, 0.85, 0.4, 0.5);
			cardTotalLabel.styleName = Label.ALTERNATE_STYLE_NAME_HEADING;
			
			_pageIndicator = new PageIndicator();
			var pageIndicatorLayoutData:AnchorLayoutData = new AnchorLayoutData();
			pageIndicatorLayoutData.left = 0;
			pageIndicatorLayoutData.right = 0;
			pageIndicatorLayoutData.verticalCenter = 0;
			_pageIndicator.addEventListener(Event.CHANGE, pageIndicator_changeHandler);
			_pageIndicator.layoutData = pageIndicatorLayoutData;
			
			header.leftItems = new <DisplayObject>
			[
				_pageIndicator
			];
			
			header.rightItems = new <DisplayObject>
			[
				cardTotalLabel
			];
			return header;
		}
		

		protected function list_scrollHandler(event:Event):void
		{
			this._pageIndicator.pageCount = this.horizontalPageCount;
			this._pageIndicator.selectedIndex = this.horizontalPageIndex;
		}

		protected function pageIndicator_changeHandler(event:Event):void
		{
			this.scrollToPageIndex(this._pageIndicator.selectedIndex, 0, this.pageThrowDuration);
		}
		
		override public function dispose():void 
		{
			this.removeEventListeners(Event.SCROLL);
			for each(var ct:CardThumb in _children) {
				ct.removeEventListeners(Event.TRIGGERED);
				ct.dispose();
			}
			_pageIndicator.removeEventListeners(Event.CHANGE);
			
			super.dispose();
		}
		
		public function get selectedCardId():uint {
			return (selectedCard) ? uint(selectedCard.name) : null;
		}
		
		public function get pageIndicator():PageIndicator 
		{
			return _pageIndicator;
		}
	}

}