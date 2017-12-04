package tto.screens 
{
	import feathers.controls.Button;
	import feathers.controls.Header;
	import feathers.controls.List;
	import feathers.controls.Panel;
	import feathers.controls.Screen;
	import feathers.data.ListCollection;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.events.Event;
	import tto.controls.MGPLabel;
	import tto.datas.BoosterItem;
	import tto.datas.CardItem;
	import tto.datas.PotionItem;
	import tto.datas.Save;
	import tto.display.ItemIcon;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class shopScreen extends Screen 
	{
		private var userBar:UserBar;
		private var shopPanel:Panel;
		private var shopList:List;
		private var buyBtn:Button;
		private var backButton:starling.display.Button;
		
		private static var FF14_SHOP:Array;
		private static var FF8_SHOP:Array;
		
		public function shopScreen() 
		{
			super();
			
			FF14_SHOP = [
				{label:i18n.gettext("STR_MGP_BOOST"), icon:new ItemIcon('potionItem'), accessory:new MGPLabel(50), item:new PotionItem(PotionItem.POTION_TYPE_MGP)},
				{label:i18n.gettext("STR_XP_BOOST"), icon:new ItemIcon('potionItem'), accessory:new MGPLabel(50), item:new PotionItem(PotionItem.POTION_TYPE_XP)},
				{label:i18n.gettext("STR_BRONZE_BOOSTER"), icon:new ItemIcon('booster_pack_icon'), accessory:new MGPLabel(520), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_BRONZE)},
				{label:i18n.gettext("STR_SILVER_BOOSTER"), icon:new ItemIcon('booster_pack_icon'), accessory:new MGPLabel(1152), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_SILVER)},
				{label:i18n.gettext("STR_GOLD_BOOSTER"), icon:new ItemIcon('booster_pack_icon'), accessory:new MGPLabel(2160), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_GOLD)},
				{label:i18n.gettext("STR_MITHRIL_BOOSTER"), icon:new ItemIcon('booster_pack_icon'), accessory:new MGPLabel(8000), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_MITHRIL)},
				{label:i18n.gettext("STR_BEAST_BOOSTER"), icon:new ItemIcon('beast_booster'), accessory:new MGPLabel(360), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_BEAST)},
				{label:i18n.gettext("STR_SCION_BOOSTER"), icon:new ItemIcon('scion_booster'), accessory:new MGPLabel(1152), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_SCION)},
				{label:i18n.gettext("STR_PRIMAL_BOOSTER"), icon:new ItemIcon('primal_booster'), accessory:new MGPLabel(3280), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_PRIMAL)},
				{label:i18n.gettext("STR_GARLEAN_BOOSTER"), icon:new ItemIcon('garlean_booster'), accessory:new MGPLabel(2160), item:new BoosterItem(BoosterItem.BOOSTER_TYPE_GARLEAN)},
				{label:i18n.gettext('STR_FF14_CARD_2'), icon:new ItemIcon('ff14_thumb_2'), accessory:new MGPLabel(120), item:new CardItem(2)},
				{label:i18n.gettext('STR_FF14_CARD_13'), icon:new ItemIcon('ff14_thumb_13'), accessory:new MGPLabel(150), item:new CardItem(13)},
				{label:i18n.gettext('STR_FF14_CARD_20'), icon:new ItemIcon('ff14_thumb_20'), accessory:new MGPLabel(200), item:new CardItem(20)},
				{label:i18n.gettext('STR_FF14_CARD_44'), icon:new ItemIcon('ff14_thumb_44'), accessory:new MGPLabel(1000), item:new CardItem(44)},
				{label:i18n.gettext('STR_FF14_CARD_45'), icon:new ItemIcon('ff14_thumb_45'), accessory:new MGPLabel(1200), item:new CardItem(45)},
				{label:i18n.gettext('STR_FF14_CARD_114'), icon:new ItemIcon('ff14_thumb_114'), accessory:new MGPLabel(14400), item:new CardItem(114)},
				{label:i18n.gettext('STR_FF14_CARD_138'), icon:new ItemIcon('ff14_thumb_138'), accessory:new MGPLabel(20000), item:new CardItem(138)},
				{label:i18n.gettext('STR_FF14_CARD_63'), icon:new ItemIcon('ff14_thumb_63'), accessory:new MGPLabel(400000), item:new CardItem(63)},
				{label:i18n.gettext('STR_FF14_CARD_118'), icon:new ItemIcon('ff14_thumb_118'), accessory:new MGPLabel(200000), item:new CardItem(118)},
				{label:i18n.gettext('STR_FF14_CARD_74'), icon:new ItemIcon('ff14_thumb_74'), accessory:new MGPLabel(1000000), item:new CardItem(74)},
			];
			FF8_SHOP = [
				{label:i18n.gettext("STR_MGP_BOOST"), icon:new ItemIcon('potionItem'), accessory:new MGPLabel(50), item:new PotionItem(PotionItem.POTION_TYPE_MGP)},
				{label:i18n.gettext("STR_XP_BOOST"), icon:new ItemIcon('potionItem'), accessory:new MGPLabel(50), item:new PotionItem(PotionItem.POTION_TYPE_XP)},
				{label:i18n.gettext('STR_FF8_CARD_32'), icon:new ItemIcon('ff8_thumb_32'), accessory:new MGPLabel(350), item:new CardItem(32)},
				{label:i18n.gettext('STR_FF8_CARD_37'), icon:new ItemIcon('ff8_thumb_37'), accessory:new MGPLabel(420), item:new CardItem(37)},
				{label:i18n.gettext('STR_FF8_CARD_45'), icon:new ItemIcon('ff8_thumb_45'), accessory:new MGPLabel(600), item:new CardItem(45)},
			];
		}
		
		override protected function draw():void
		{
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			shopPanel.x = 8;
			shopPanel.y = 96;
			shopPanel.height = stage.height - 96 -54;
			//shopList.height = shopPanel.height - 16;
		}
		override protected function initialize():void
		{
			super.initialize();
			
			userBar = new UserBar('SHOP');
			addChild(userBar);
			
			shopPanel = new Panel();
			shopPanel.title = i18n.gettext('STR_CARD_SHOP');
			shopPanel.headerFactory = headerFactory;
			shopPanel.footerFactory = shopPanelFooter;
			this.addChild(shopPanel);
			
			shopList = new List();
			var boosterPackIcon:Image = new Image(Assets.manager.getTexture('booster_pack_icon'));
			var shopListCollection:ListCollection = new ListCollection(shopScreen[String(Game.PROFILE_DATAS.MODE).toUpperCase()+'SHOP']);
			shopList.dataProvider = shopListCollection;
			shopList.snapToPages = false;
			shopList.addEventListener(Event.CHANGE, shopList_changeHandler);
			shopPanel.addChild(shopList);
		}
		
		
		private function headerFactory():Header
		{
			var header:Header = new Header();
			
			backButton = new starling.display.Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.y = stage.height - backButton.height;
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			
			header.leftItems =  new <DisplayObject>
			[
				backButton
			];
			return header;
		}
		
		private function backButton_triggeredHandler(e:Event):void 
		{
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		private function shopPanelFooter():Header 
		{
			var footer:Header = new Header();
			buyBtn = new feathers.controls.Button();
			buyBtn.isEnabled = false;
			buyBtn.label = i18n.gettext('STR_BUY');
			buyBtn.addEventListener(Event.TRIGGERED, buyButton_triggeredHandler);
			footer.rightItems = new <DisplayObject>
			[
				buyBtn
			];
			return footer;
		}
		
		private function buyButton_triggeredHandler(e:Event):void 
		{
			Game.PROFILE_DATAS.MGP -= shopList.selectedItem.accessory.text;
			this.userBar.updateMGP();
			buyBtn.isEnabled = (int(Game.PROFILE_DATAS.MGP) >= int(shopList.selectedItem.accessory.text)) ? true : false;
			Game.PROFILE_DATAS.BAG.push(shopList.selectedItem.item.__toJSON());
			InventoryScreen.sortBag();
			//Save.save(Game.PROFILE_DATAS);
		}
		
		private function shopList_changeHandler(e:Event):void 
		{
			buyBtn.isEnabled = (int(Game.PROFILE_DATAS.MGP) >= int(shopList.selectedItem.accessory.text)) ? true : false;
		}
		
		override public function dispose():void
		{
			shopList.removeEventListener(Event.CHANGE, shopList_changeHandler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			buyBtn.removeEventListener(Event.TRIGGERED, buyButton_triggeredHandler);
			super.dispose();
		}
	}

}