package tto.utils {
	import flash.display.MovieClip;
	import flash.display.Bitmap;
	import flash.display.BitmapData;
	public class gfx {
		public function gfx() {
			//  gfx  //------------------------------------------------------------------------------------------------------;
		}
		public static function traceBackgroundColor(container:MovieClip, color:uint):void {
			tools.purge(container);
			
			var bmd:BitmapData = new BitmapData(1024, 768, false, color);
			var bm:Bitmap = new Bitmap(bmd);
			container.addChild(bm);
			container.mouseChildren = false;
		}
		public static function trace_cube(container:MovieClip, width:uint, cubeNb:uint, density:uint, alpha:Number):void {
			// Create a single 20 x 20 pixel bitmap, non-transparent
			var myImage:BitmapData = new BitmapData(width,width,false,0xFFFFFF);
			var myContainer:Bitmap;
			for (var i:int = 0; i<cubeNb; i++) {
				// Create a container referencing the BitmapData instance
				myContainer = new Bitmap(myImage);
				// Add it to the DisplayList 
				container.addChild(myContainer);
				// Place each container 
				myContainer.x = (myContainer.width + 8) * Math.round(i % density);
				myContainer.y = (myContainer.height + 8) * int(i / density);
				// Set a specific rotation, alpha, and depth 
				myContainer.rotation = Math.random() * 360;
				myContainer.alpha = Math.random() * alpha;
				myContainer.scaleX = myContainer.scaleY = Math.max(Math.random(), 0.1);
			}
			//myImage.dispose();
			myImage = null;
			myContainer = null;
			container.mouseChildren = false;
		}
	}
}