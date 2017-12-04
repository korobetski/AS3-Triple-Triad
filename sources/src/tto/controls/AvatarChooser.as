package tto.controls {
	import feathers.controls.Callout;
	import feathers.controls.LayoutGroup;
	import feathers.layout.TiledRowsLayout;
	import starling.display.BlendMode;
	import starling.display.Image;
	import starling.events.Event;
	import starling.events.Touch;
	import starling.events.TouchEvent;
	import starling.events.TouchPhase;
	import starling.textures.Texture;
	import tto.datas.Save;
	import tto.display.ImageExtended;
	import tto.Game;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class AvatarChooser extends Image {
		private var avatarTexturesNames:Vector.<String>;
		private var _width:uint;
		private var menu:LayoutGroup;
		private var callout:Callout;
		private var _selectedAvatarId:String = 'ffxiv_twi01001';
		
		public function AvatarChooser(texture:Texture, _w:uint) {
			super(texture);
			this.blendMode = BlendMode.NONE;
			width = _width = _w;
			height = width;
			
			avatarTexturesNames = Assets.manager.getTextureNames('ffxiv_twi')
			
			useHandCursor = true;
			addEventListener(TouchEvent.TOUCH, onTouch);
		}
		
		private function onTouch(event:TouchEvent):void {
			
			var touch:Touch = event.getTouch(this, TouchPhase.ENDED);
			if (touch) {
				menu = new LayoutGroup();
				menu.width = _width * 0.75 * 5 + 16;
				var tileLayout:TiledRowsLayout = new TiledRowsLayout();
				tileLayout.gap = 0;
				tileLayout.padding = 0;
				tileLayout.useSquareTiles = true;
				menu.layout = tileLayout;
				
				var avatarNb:uint = avatarTexturesNames.length;
				var img:ImageExtended
				for (var i:uint; i < avatarNb; i++) {
					img = new ImageExtended(avatarTexturesNames[i]);
					img.blendMode = BlendMode.NONE;
					img.width = img.height = _width * 0.75;
					img.useHandCursor = true;
					menu.addChild(img);
				}
				menu.addEventListener(Event.TRIGGERED, avatarHandler);
				callout = Callout.show(menu, this);
			}
		}
		
		public function get selectedAvatarId():String {
			return _selectedAvatarId;
		}
		
		private function avatarHandler(e:Event):void {
			var avatar:ImageExtended = e.target as ImageExtended;
			this.texture = avatar.texture;
			_selectedAvatarId = avatar.textureName;
			callout.close(true);
			
			if (Game.LOGGED_IN) {
				Game.PROFILE_DATAS.AVATAR_ID = avatar.textureName;
				Save.save(Game.PROFILE_DATAS);
			}
		}
		
		override public function dispose():void {
			removeEventListener(TouchEvent.TOUCH, onTouch);
			
			if (menu && menu.hasEventListener(Event.CHANGE))
				menu.removeEventListener(Event.CHANGE, avatarHandler);
			super.dispose();
		}
	}

}