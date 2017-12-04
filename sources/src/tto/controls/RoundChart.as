package tto.controls 
{
	import feathers.controls.Label;
	import flash.display.Bitmap;
	import flash.display.BitmapData;
	import starling.display.Image;
	import starling.display.Sprite;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class RoundChart extends Sprite 
	{
		private var _width:uint;
		private var gap:uint = 2;
		
		public function RoundChart(segments:Array, _w:uint, bigText:String, smallText:String) 
		{
			super();
			
			_width = _w;
			
			this.touchable = false;
			var chart:flash.display.Sprite = new flash.display.Sprite();
			
			chart.graphics.beginFill(0x303030, 1);
            chart.graphics.lineStyle(0, 0x303030, 0);
            //chart.graphics.drawCircle(_width/2, _width/2, _width*1.1);
			
            chart.graphics.beginFill(0x000000, 0.1);
            chart.graphics.drawCircle(_width, _width, _width);
			
			var nextRatio:Number = 0;
			for each(var seg:Object in segments) {
				chart.graphics.beginFill(uint(seg.color), 1);
				drawSegment(chart, _width, _width, _width, Number(nextRatio + gap), Number(nextRatio+seg.ratio - gap), 1);
				nextRatio = nextRatio + Number(seg.ratio);
			}
			
            chart.graphics.beginFill(0x303030, 1);
            chart.graphics.lineStyle(0, 0x303030, 0);
            chart.graphics.drawCircle(_width, _width, _width*0.75);
			chart.graphics.endFill();
			
			var bmpData:BitmapData = new BitmapData( _width*2,_width*2, true, 0x303030);
			bmpData.draw(chart);
			var bmp:Image = Image.fromBitmap(new Bitmap(bmpData, "auto", true));

			addChild(bmp); 
			
			var totalTF:Label = new Label();
			totalTF.text = bigText;
			totalTF.styleName = Label.ALTERNATE_STYLE_NAME_HEADING
			totalTF.y = Math.round((_width - totalTF.height)/2)+4;
			totalTF.x = Math.round((_width - totalTF.width)/2);
			addChild(totalTF);
			
			/*
			var matchesTF:TextField = new TextField();
			matchesTF.y = Math.ceil(_width / 6)*4;
			matchesTF.width = _width;
			matchesTF.text = smallText;
			matchesTF.autoSize = "center";
			matchesTF.selectable = false;
			matchesTF.embedFonts = true;
			matchesTF.setTextFormat(tf);
			addChild(matchesTF);
			*/
		}
		
		/**
		 * Draw a segment of a circle
		 * @param target	<Sprite> The object we want to draw into
		 * @param x			<Number> The x-coordinate of the origin of the segment
		 * @param y 		<Number> The y-coordinate of the origin of the segment
		 * @param r 		<Number> The radius of the segment
		 * @param aStart	<Number> The starting angle (degrees) of the segment (0 = East)
		 * @param aEnd		<Number> The ending angle (degrees) of the segment (0 = East)
		 * @param step		<Number=1> The number of degrees between each point on the segment's circumference
		 */
		private function drawSegment(target:flash.display.Sprite, x:Number, y:Number, r:Number, aStart:Number, aEnd:Number, step:Number = 1):void {
				// More efficient to work in radians
				var degreesPerRadian:Number = Math.PI / 180;
				aStart *= degreesPerRadian;
				aEnd *= degreesPerRadian;
				step *= degreesPerRadian;
				
				// Draw the segment
				target.graphics.moveTo(x, y);
				for (var theta:Number = aStart; theta < aEnd; theta += Math.min(step, aEnd - theta)) {
					target.graphics.lineTo(x + r * Math.cos(theta), y + r * Math.sin(theta));
				}
				target.graphics.lineTo(x + r * Math.cos(aEnd), y + r * Math.sin(aEnd));
				target.graphics.lineTo(x, y);
		};
		
	}

}