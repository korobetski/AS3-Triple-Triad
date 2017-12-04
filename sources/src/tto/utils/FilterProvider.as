package tto.utils {
	import flash.filters.DropShadowFilter;
	import starling.filters.BlurFilter
	
	/**
	 * ...
	 * @author Mao
	 */
	public class FilterProvider {
		
		public static var blackDropShadow:DropShadowFilter = new DropShadowFilter(0, 45, 0x404040, 0.8, 3, 3, 3.0, 1, false, false, false);
		public static var whiteBorder:DropShadowFilter = new DropShadowFilter(0, 0, 0xFFFFFF, 1, 1.6, 1.6, 1.6, 1, false, false, false);
		public static var blackBorder:DropShadowFilter = new DropShadowFilter(1, 90, 0x000000, 0.6, 3, 3, 2.0, 1, false, false, false);
		public static var fatBlackDropShadow:DropShadowFilter = new DropShadowFilter(10, 90, 0x000000, 0.7, 0, 25, 0.5, 1, false, false, false);
		public static var goldDropShadow:DropShadowFilter = new DropShadowFilter(0, 45, 0xe2c380, 0.8, 6, 6, 3.0, 1, false, false, false);
		public static var goldGlow:DropShadowFilter = new DropShadowFilter(0, 45, 0xffffd6, 0.5, 15, 15, 2.0, 1, false, false, false);
		public static var cardThumb_DSF:DropShadowFilter = new DropShadowFilter(0, 45, 0xffffd6, 0.9, 8, 8, 5.0, 1, false, false, false);
		public static var materialDesignBoxShadow:BlurFilter = BlurFilter.createDropShadow(3, 90 * (Math.PI / 180), 0x000000, 0.7, 1, 0.5);
		public static var menuDropShadow:BlurFilter = BlurFilter.createDropShadow(0, 0, 0x000000, 0.85, 1.0, 0.5);
		public static var blueDropShadow:DropShadowFilter = new DropShadowFilter(0, 45, 0x253746, 0.8, 6, 6, 3.0, 1, false, false, false);
		
		public function FilterProvider() {
		
		}
	
	}

}