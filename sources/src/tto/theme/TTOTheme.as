/*
 Copyright 2012-2015 Joshua Tynjala

 Permission is hereby granted, free of charge, to any person
 obtaining a copy of this software and associated documentation
 files (the "Software"), to deal in the Software without
 restriction, including without limitation the rights to use,
 copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the
 Software is furnished to do so, subject to the following
 conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 OTHER DEALINGS IN THE SOFTWARE.
 */
package tto.theme
{
	import flash.display.Bitmap;
	import flash.display.BitmapData;

	import starling.events.Event;
	import starling.textures.Texture;
	import starling.textures.TextureAtlas;

	/**
	 * The "Metal Works" theme for mobile Feathers apps.
	 *
	 * <p>This version of the theme embeds its assets. To load assets at
	 * runtime, see <code>MetalWorksMobileThemeWithAssetManager</code> instead.</p>
	 *
	 * @see http://feathersui.com/help/theme-assets.html
	 */
	public class TTOTheme extends BaseTTOTheme
	{
		/**
		 * @private
		 */
		[Embed(source="../../../assets/tto_mobile.xml",mimeType="application/octet-stream")] protected static const ATLAS_XML:Class;

		/**
		 * @private
		 */
		[Embed(source="../../../assets/tto_mobile.png")] protected static const ATLAS_BITMAP:Class;
		
		
		/**
		 * @private
		 */
		[Embed(source="../../../assets/ui/ff14_ui.xml",mimeType="application/octet-stream")] protected static const FF14_UI_XML:Class;

		/**
		 * @private
		 */
		[Embed(source="../../../assets/ui/ff14_ui.png")] protected static const FF14_UI_BITMAP:Class;

		/**
		 * Constructor.
		 */
		public function TTOTheme(scaleToDPI:Boolean = true)
		{
			super(scaleToDPI);
			this.initialize();
			this.dispatchEventWith(Event.COMPLETE);
		}

		/**
		 * @private
		 */
		override protected function initialize():void
		{
			this.initializeTextureAtlas();
			super.initialize();
		}

		/**
		 * @private
		 */
		protected function initializeTextureAtlas():void
		{
			var atlasBitmapData:BitmapData = Bitmap(new ATLAS_BITMAP()).bitmapData;
			var atlasTexture:Texture = Texture.fromBitmapData(atlasBitmapData, false);
			atlasTexture.root.onRestore = this.atlasTexture_onRestore;
			atlasBitmapData.dispose();
			this.atlas = new TextureAtlas(atlasTexture, XML(new ATLAS_XML()));
			
			var ff14_ui_atlasBitmapData:BitmapData = Bitmap(new FF14_UI_BITMAP()).bitmapData;
			var ff14_ui_atlasTexture:Texture = Texture.fromBitmapData(ff14_ui_atlasBitmapData, false);
			ff14_ui_atlasTexture.root.onRestore = this.atlasTexture_onRestore;
			ff14_ui_atlasBitmapData.dispose();
			this.ff14_ui_atlas = new TextureAtlas(ff14_ui_atlasTexture, XML(new FF14_UI_XML()));
		}

		/**
		 * @private
		 */
		protected function atlasTexture_onRestore():void
		{
			var atlasBitmapData:BitmapData = Bitmap(new ATLAS_BITMAP()).bitmapData;
			this.atlas.texture.root.uploadBitmapData(atlasBitmapData);
			atlasBitmapData.dispose();
			
			var ff14_ui_atlasBitmapData:BitmapData = Bitmap(new FF14_UI_BITMAP()).bitmapData;
			this.ff14_ui_atlas.texture.root.uploadBitmapData(ff14_ui_atlasBitmapData);
			ff14_ui_atlasBitmapData.dispose();
		}
	}
}
