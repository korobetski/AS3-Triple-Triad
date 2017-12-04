package tto.screens {
	import feathers.controls.LayoutGroup;
	import feathers.controls.Screen;
	import feathers.layout.VerticalLayout;
	import starling.display.DisplayObject;
	import starling.events.Event;
	import tto.controls.MainButton;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class dashboardScreen extends Screen {
		private var menuContainer:LayoutGroup;
		private var menuItems:Array;
		private var vL:VerticalLayout;
		private var userBar:UserBar;
		
		public function dashboardScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			vL = new VerticalLayout();
			vL.paddingLeft = 64;
			vL.paddingTop = 200;
			vL.gap = 8;
			menuContainer.layout = vL;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			userBar = new UserBar('DASHBOARD');
			addChild(userBar);
			
			menuContainer = new LayoutGroup();
			addChild(menuContainer);
			
			menuItems = [];
			menuItems.push({label: 'STR_PLAY', screenId: 'PVE_SCREEN'});
			menuItems.push({label: 'STR_MULTIPLAYER', screenId: 'PVP_SCREEN', enabled:true});
			menuItems.push({label: 'STR_PROFILE', screenId: 'PROFILE'});
			menuItems.push({label: 'STR_CARD_LIST', screenId: 'CARD_LIST'});
			menuItems.push({label: 'STR_CARD_DECKS', screenId: 'CARD_DECKS'});
			menuItems.push({label: 'STR_INVENTORY', screenId: 'INVENTORY_SCREEN'});
			menuItems.push({label: 'STR_SHOP', screenId: 'SHOP'});
			if (Game.PROFILE_DATAS.ADMIN)
				menuItems.push({label: 'STR_BACKSTAGE', screenId: 'BACKSTAGE'});
			menuItems.push({label: 'STR_HELP', screenId: 'HELP_SCREEN'});
			menuItems.push({label: 'STR_LOGOUT', screenId: 'MENU_SCREEN'});
			
			var itemCount:int = menuItems.length;
			var menuItemDatas:Object
			var newMenuItem:MainButton
			for (var i:int = 0; i < itemCount; i++) {
				menuItemDatas = menuItems[i];
				newMenuItem = new MainButton(i18n.gettext(menuItemDatas.label));
				if (menuItemDatas.enabled == false) newMenuItem.enabled = false;
				menuContainer.addChild(newMenuItem);
			}
			menuContainer.addEventListener(Event.TRIGGERED, menuController);
		}
		
		private function menuController(e:Event):void {
			var index:int = menuContainer.getChildIndex(e.target as DisplayObject);
			var data:Object = menuItems[index];
			
			if (data.screenId) {
				dispatchEventWith('gotoScreen', false, data.screenId);
			}
		}
		
		override public function dispose():void {
			menuContainer.removeEventListener(Event.TRIGGERED, menuController);
			super.dispose();
		}
	
	}

}