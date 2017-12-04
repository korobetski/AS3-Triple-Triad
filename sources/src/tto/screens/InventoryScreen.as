package tto.screens {
	import com.adobe.utils.ArrayUtil;
	import feathers.controls.Button;
	import feathers.controls.Header;
	import feathers.controls.List;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.data.ListCollection;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.anims.UnlockCardAnim;
	import tto.datas.BoosterItem;
	import tto.datas.CardItem;
	import tto.datas.Item;
	import tto.datas.PotionItem;
	import tto.datas.Save;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class InventoryScreen extends Screen {
		private var userBar:UserBar;
		private var inventoryPanel:Panel;
		private var inventoryList:List;
		private var backButton:starling.display.Button;
		private var dropBtn:Button;
		private var sellBtn:Button;
		private var useBtn:Button;
		
		public function InventoryScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			inventoryPanel.width = stage.width / 2 - 8;
			inventoryPanel.height = stage.height - 96 - 48;
			
			inventoryList.width = inventoryPanel.width - 16;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			userBar = new UserBar('INVENTORY_SCREEN');
			addChild(userBar);
			
			inventoryPanel = new Panel();
			inventoryPanel.x = 16;
			inventoryPanel.y = 96;
			inventoryPanel.title = i18n.gettext('STR_INVENTORY');
			inventoryPanel.headerFactory = headerFactory;
			inventoryPanel.footerFactory = inventoryFooter;
			addChild(inventoryPanel);
			
			inventoryList = new List();
			inventoryPanel.addChild(inventoryList);
			refreshList();
			
			inventoryList.itemRendererProperties.labelField = "name";
			inventoryList.itemRendererProperties.iconField = "icon";
			//inventoryList.itemRendererProperties.accessoryPosition = "bottom";
			inventoryList.addEventListener(Event.CHANGE, listHandler);
		/*
		   var inventoryLayout:TiledRowsLayout = new TiledRowsLayout();
		   inventoryLayout.padding = 8;
		   inventoryLayout.gap = 8;
		   inventoryPanel.layout = inventoryLayout;
		 */
		}
		
		private function refreshList():void {
			var selectedIndex:int = inventoryList.selectedIndex;
			var inventoryLC:ListCollection = new ListCollection();
			var bagLength:uint = Game.PROFILE_DATAS.BAG.length;
			for (var i:uint = 0; i < bagLength; i++) {
				var itemStack:Object = Game.PROFILE_DATAS.BAG[i];
				var item:Item = Item.itemize(itemStack);
				if (item is CardItem) {
					item.iconId = (Game.PROFILE_DATAS.MODE + 'thumb_' + String((item as CardItem).cardId));
				}
				item.bagIndex = i;
				/*
				   var invI:InventoryItem = new InventoryItem(item);
				   inventoryPanel.addChild(invI);
				 */
				inventoryLC.push(item);
			}
			inventoryList.dataProvider = inventoryLC;
			if (inventoryLC.length >= (selectedIndex + 1))
				inventoryList.selectedIndex = selectedIndex;
			//useBtn.isEnabled = dropBtn.isEnabled = sellBtn.isEnabled = false;
		}
		
		private function listHandler(e:Event):void {
			if (inventoryList.selectedItem !== null) {
				var item:Item = inventoryList.selectedItem as Item;
				useBtn.isEnabled = (item.useable) ? true : false;
				dropBtn.isEnabled = (item.dropable) ? true : false;
				sellBtn.isEnabled = (item.sellable) ? true : false;
				
				if (item is CardItem && ArrayUtil.arrayContainsValue(Game.PROFILE_DATAS.CARDS, inventoryList.selectedItem.cardId)) {
					useBtn.isEnabled = false;
				}
			} else {
				useBtn.isEnabled = dropBtn.isEnabled = sellBtn.isEnabled = false;
			}
		}
		
		private function headerFactory():Header {
			var header:Header = new Header();
			
			backButton = new starling.display.Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.y = stage.height - backButton.height;
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			
			header.leftItems = new <DisplayObject>[backButton];
			return header;
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		private function inventoryFooter():Header {
			var footer:Header = new Header();
			dropBtn = new Button();
			dropBtn.isEnabled = false;
			dropBtn.styleName = Button.ALTERNATE_STYLE_NAME_DANGER_BUTTON;
			dropBtn.label = i18n.gettext('STR_DISCARD');
			dropBtn.addEventListener(Event.TRIGGERED, dropBtnHandler);
			
			sellBtn = new Button();
			sellBtn.isEnabled = false;
			sellBtn.label = i18n.gettext('STR_SELL');
			sellBtn.addEventListener(Event.TRIGGERED, sellBtnHandler);
			
			useBtn = new Button();
			useBtn.isEnabled = false;
			useBtn.label = i18n.gettext('STR_USE');
			useBtn.addEventListener(Event.TRIGGERED, useBtnHandler);
			
			var sortBtn:Button = new Button();
			sortBtn.label = i18n.gettext('STR_SORT');
			sortBtn.addEventListener(Event.TRIGGERED, sortBtnHandler);
			
			footer.leftItems = new <DisplayObject>[dropBtn];
			footer.rightItems = new <DisplayObject>[sellBtn, useBtn, sortBtn];
			return footer;
		}
		
		private function sortBtnHandler(e:Event):void {
			InventoryScreen.sortBag();
			refreshList();
		}
		
		public static function sortBag():void {
			var bagLength:uint = Game.PROFILE_DATAS.BAG.length;
			if (bagLength > 0) {
				var sorter:Object = new Object();
				for (var i:uint = 0; i < bagLength; i++) {
					var itemStack:Object = Game.PROFILE_DATAS.BAG[i];
					if (itemStack.type == Item.ITEM_TYPE_CARD) {
						var itemCode:String = itemStack.type + '_' + itemStack.card;
						if (!sorter[itemCode])
							sorter[itemCode] = itemStack;
						else
							sorter[itemCode].stack = uint(sorter[itemCode].stack) + uint(itemStack.stack);
					} else if (itemStack.type == Item.ITEM_TYPE_BOOSTER) {
						itemCode = itemStack.type + '_' + itemStack.booster;
						if (!sorter[itemCode])
							sorter[itemCode] = itemStack;
						else
							sorter[itemCode].stack = uint(sorter[itemCode].stack) + uint(itemStack.stack);
					} else if (itemStack.type == Item.ITEM_TYPE_POTION) {
						itemCode = itemStack.type + '_' + itemStack.potion;
						if (!sorter[itemCode])
							sorter[itemCode] = itemStack;
						else
							sorter[itemCode].stack = uint(sorter[itemCode].stack) + uint(itemStack.stack);
					}
				}
				
				Game.PROFILE_DATAS.BAG = new Array();
				for each (itemStack in sorter) {
					Game.PROFILE_DATAS.BAG.push(itemStack);
				}
				
				Game.PROFILE_DATAS.BAG.sortOn(['type', 'card', 'stack'], [Array.CASEINSENSITIVE, Array.NUMERIC, Array.NUMERIC]);
			}
			Save.save(Game.PROFILE_DATAS);
		}
		
		private function sellBtnHandler(e:Event):void {
			var item:Item = inventoryList.selectedItem as Item;
			if (item.sellable && item.value > 0) {
				Game.PROFILE_DATAS.MGP += item.value;
				userBar.updateMGP();
				if (item.stack == 1)
					Game.PROFILE_DATAS.BAG.splice(item.bagIndex, 1);
				else if (item.stack > 1) {
					item.stack -= 1;
					Game.PROFILE_DATAS.BAG[inventoryList.selectedItem.bagIndex] = item.__toJSON();
				}
				
				Save.save(Game.PROFILE_DATAS);
				refreshList();
			}
		}
		
		private function dropBtnHandler(e:Event):void {
			// TODO : afficher une Alert
			
			var item:Item = inventoryList.selectedItem as Item;
			if (item.stack == 1)
				Game.PROFILE_DATAS.BAG.splice(item.bagIndex, 1);
			else if (item.stack > 1) {
				item.stack -= 1;
				Game.PROFILE_DATAS.BAG[inventoryList.selectedItem.bagIndex] = item.__toJSON();
			}
			
			Save.save(Game.PROFILE_DATAS);
			refreshList();
		
		}
		
		private function useBtnHandler(e:Event):void {
			if (inventoryList.selectedItem is CardItem) {
				useBtn.isEnabled = false;
				var item:CardItem = inventoryList.selectedItem as CardItem;
				Game.PROFILE_DATAS.CARDS.push(item.cardId);
				
				var cardAnim:UnlockCardAnim = new UnlockCardAnim();
				this.addChild(cardAnim);
				cardAnim.start(item.cardId);
				
				if (item.stack == 1)
					Game.PROFILE_DATAS.BAG.splice(item.bagIndex, 1);
				else if (item.stack > 1) {
					item.stack -= 1;
					Game.PROFILE_DATAS.BAG[inventoryList.selectedItem.bagIndex] = item.__toJSON();
				}
			} else if (inventoryList.selectedItem is BoosterItem) {
				var boosterItem:BoosterItem = inventoryList.selectedItem as BoosterItem;
				// on ouvre le booster et on détermine la carte gagnée
				var cardId:uint = boosterItem.open();
				var boosterCard:CardItem = new CardItem(cardId);
				// on ajoute la carte dans l'inventaire
				Game.PROFILE_DATAS.BAG.push(boosterCard.__toJSON());
				if (boosterItem.stack == 1) {
					useBtn.isEnabled = false;
					Game.PROFILE_DATAS.BAG.splice(boosterItem.bagIndex, 1);
				} else if (boosterItem.stack > 1) {
					boosterItem.stack -= 1;
					Game.PROFILE_DATAS.BAG[inventoryList.selectedItem.bagIndex] = boosterItem.__toJSON();
				}
			} else if (inventoryList.selectedItem is PotionItem) {
				var potionItem:PotionItem = inventoryList.selectedItem as PotionItem;
				if (!Game.PROFILE_DATAS.BOONS)
					Game.PROFILE_DATAS.BOONS = {MGP: 0, XP: 0, LUCK: 0};
				Game.PROFILE_DATAS.BOONS[potionItem.modifier.type] += potionItem.modifier.value; //{type:"MGP", value:10}
				if (potionItem.stack == 1) {
					useBtn.isEnabled = false;
					Game.PROFILE_DATAS.BAG.splice(potionItem.bagIndex, 1);
				} else if (potionItem.stack > 1) {
					potionItem.stack -= 1;
					Game.PROFILE_DATAS.BAG[inventoryList.selectedItem.bagIndex] = potionItem.__toJSON();
				}
				userBar.updateBoons();
			}
			InventoryScreen.sortBag();
			refreshList();
		}
		
		override public function dispose():void {
			super.dispose();
		}
	}
}