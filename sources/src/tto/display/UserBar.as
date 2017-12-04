package tto.display {
	import feathers.controls.Callout;
	import feathers.controls.Label;
	import feathers.controls.List;
	import feathers.core.FeathersControl;
	import feathers.data.ListCollection;
	import starling.display.Image;
	import starling.display.Quad;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import starling.filters.BlurFilter;
	import tto.controls.MGPLabel;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class UserBar extends FeathersControl {
		private var _currentScreen:String;
		private var menu:List;
		private var MGP:MGPLabel;
		private var mgpBoonIcon:Image;
		private var xpBoonIcon:Image;
		private var username:Label;
		
		override protected function draw():void {
			super.draw();
			this.width = 340;
			username.x = 58;
			username.y = 12;
			
			MGP.x = 260;
			xpBoonIcon.x = MGP.x - xpBoonIcon.width - 32;
			mgpBoonIcon.x = xpBoonIcon.x + mgpBoonIcon.width;
			mgpBoonIcon.y = xpBoonIcon.y = 8
		}
		
		override protected function initialize():void {
			super.initialize();
			
			var background:Quad = new Quad(340, 50);
			background.alpha = 0.5;
			addChild(background);
			
			if (Game.PROFILE_DATAS) {
				var level:uint = Game.PROFILE_DATAS.LEVEL;
				
				var avatar:Image = new Image(Assets.manager.getTexture(Game.PROFILE_DATAS.AVATAR_ID));
				avatar.width = avatar.height = 50;
				addChild(avatar);
				
				username = new Label();
				username.minWidth = 100;
				username.styleName = Label.ALTERNATE_STYLE_NAME_HEADING;
				username.text = String(Game.PROFILE_DATAS.USERNAME);
				username.filter = BlurFilter.createDropShadow(0, 0, 0x000000, 1.2, 0.2, 0.5);
				addChild(username);
				
				// BOONS = {MGP:0, XP:0, LUCK:0}
				mgpBoonIcon = new Image(Assets.manager.getTexture('mgp_boost_icon'));
				mgpBoonIcon.visible = (uint(Game.PROFILE_DATAS.BOONS.MGP) > 0)
				addChild(mgpBoonIcon);
				xpBoonIcon = new Image(Assets.manager.getTexture('xp_boost_icon'));
				xpBoonIcon.visible = (uint(Game.PROFILE_DATAS.BOONS.XP) > 0)
				addChild(xpBoonIcon);
				
				MGP = new MGPLabel(Game.PROFILE_DATAS.MGP);
				addChild(MGP);
				
				addEventListener(TouchEvent.TOUCH, onTouch);
			}
		}
		
		public function UserBar(currentScreen:String) {
			super();
			
			_currentScreen = currentScreen;
		
		}
		
		public function updateBoons():void {
			mgpBoonIcon.visible = (uint(Game.PROFILE_DATAS.BOONS.MGP) > 0)
			xpBoonIcon.visible = (uint(Game.PROFILE_DATAS.BOONS.XP) > 0)
		}
		
		public function updateMGP():void {
			MGP.text = Game.PROFILE_DATAS.MGP;
		}
		
		private function menuHandler(e:Event):void {
			if (menu.selectedItem.screenId) {
				dispatchEventWith('gotoScreen', true, menu.selectedItem.screenId);
			}
		}
		
		private function onTouch(event:TouchEvent):void {
			var touch:Touch = event.getTouch(this, TouchPhase.ENDED);
			if (touch) {
				menu = new List();
				menu.itemRendererProperties.height = 40;
				var melc:ListCollection = new ListCollection();
				if (_currentScreen !== 'DASHBOARD')
					melc.push({label: i18n.gettext('STR_DASHBOARD'), screenId: 'DASHBOARD'});
				if (_currentScreen !== 'PVE_SCREEN')
					melc.push( { label: i18n.gettext('STR_PLAY'), screenId: 'PVE_SCREEN' } );
				/*
				if (_currentScreen !== 'PVP_SCREEN')
					melc.push({label: i18n.gettext('STR_MULTIPLAYER'), screenId: 'PVP_SCREEN'});
				*/
				if (_currentScreen !== 'PROFILE')
					melc.push({label: i18n.gettext('STR_PROFILE'), screenId: 'PROFILE'});
				if (_currentScreen !== 'CARD_LIST')
					melc.push({label: i18n.gettext('STR_CARD_LIST'), screenId: 'CARD_LIST'});
				if (_currentScreen !== 'CARD_DECKS')
					melc.push({label: i18n.gettext('STR_CARD_DECKS'), screenId: 'CARD_DECKS'});
				if (_currentScreen !== 'INVENTORY_SCREEN')
					melc.push({label: i18n.gettext('STR_INVENTORY'), screenId: 'INVENTORY_SCREEN'});
				if (_currentScreen !== 'SHOP')
					melc.push({label: i18n.gettext('STR_SHOP'), screenId: 'SHOP'});
				melc.push({label: i18n.gettext('STR_LOGOUT'), screenId: 'MENU_SCREEN'});
				menu.dataProvider = melc;
				menu.addEventListener(Event.CHANGE, menuHandler);
				Callout.show(menu, this);
			}
		}
		
		override public function dispose():void {
			removeEventListener(TouchEvent.TOUCH, onTouch);
			if (menu && menu.hasEventListener(Event.CHANGE))
				menu.removeEventListener(Event.CHANGE, menuHandler);
			tools.purge(this)
			super.dispose();
		}
	}

}