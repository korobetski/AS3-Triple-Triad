package tto.display {
	import starling.display.BlendMode;
	import starling.display.Image;
	import starling.display.Sprite;
	import starling.textures.Texture;
	import tto.utils.Assets;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class AchievementIcon extends Sprite {
		private var _icon:Image;
		private var _iconTex:Texture;
		private var _borders:Image;
		
		public function AchievementIcon(textureId:String) {
			super();
			
			_iconTex = Assets.manager.getTexture(textureId);
			_icon = new Image(_iconTex);
			addChild(_icon);
			
			_borders = new Image(Assets.manager.getTexture('achievement_border'));
			_borders.x = _borders.y = -2;
			addChild(_borders)
			this.touchable = false;
			this.flatten();
		}
		
		public function set texture(texture:Texture):void {
			_iconTex = texture;
			_icon.texture = _iconTex;
		}
		
		public function get texture():Texture {
			return _iconTex;
		}
		
		public function set textureId(textureId:String):void {
			_iconTex = Assets.manager.getTexture(textureId);
			_icon.texture = _iconTex;
		}
		
		override public function dispose():void {
			super.dispose();
		}
	}

}