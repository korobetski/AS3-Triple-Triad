package tto.datas 
{
	/**
	 * ...
	 * @author Mao
	 */
	public class Rank 
	{
		public static const steps:Vector.<uint> = new <uint>[0, 250, 450, 810, 1458, 2624, 4723, 8501, 15302, 27544, 49579, 89242, 160636, 289145, 520461, 936830, 1686294, 3035329, 5463592, 9834466, 17702039, 31863670];
		
		public function Rank() 
		{
			
		}
		
		public static function xpToRank(xp:uint):uint {
			var level:uint = 1;
			for (var i:uint = 0; i < steps.length; i++ ) {
				if (xp >= steps[i]) {
					if (xp < steps[i+1]) {
						level = (i + 1);
						break;
					} else {
						continue;
					}
				} else {
					level = (i);
					break;
				}
			}
			
			return level;
		}
	}

}