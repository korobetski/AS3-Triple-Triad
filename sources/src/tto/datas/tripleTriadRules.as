package tto.datas {
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class tripleTriadRules {
		public static const RULE_OPEN:String = 'STR_OPEN';
		public static const RULE_DEFAULT_OPEN:String = 'RULE_DEFAULT_OPEN';
		public static const RULE_ALL_OPEN:String = 'RULE_ALL_OPEN';
		public static const RULE_THREE_OPEN:String = 'RULE_THREE_OPEN';
		public static const RULE_SUDDEN_DEATH:String = 'RULE_SUDDEN_DEATH';
		public static const RULE_RANDOM:String = 'RULE_RANDOM';
		public static const RULE_DEFAULT_ORDER:String = 'RULE_DEFAULT_ORDER';
		public static const RULE_ORDER:String = 'RULE_ORDER';
		public static const RULE_CHAOS:String = 'RULE_CHAOS';
		public static const RULE_REVERSE:String = 'RULE_REVERSE';
		public static const RULE_FALLEN_ACE:String = 'RULE_FALLEN_ACE';
		public static const RULE_SAME:String = 'RULE_SAME';
		public static const RULE_SAME_WALL:String = 'RULE_SAME_WALL';
		public static const RULE_PLUS:String = 'RULE_PLUS';
		public static const RULE_COMBO:String = 'RULE_COMBO';
		public static const RULE_TYPE:String = 'STR_TYPE';
		public static const RULE_DEFAULT_TYPE:String = 'RULE_DEFAULT_TYPE';
		public static const RULE_ASCENSION:String = 'RULE_ASCENSION';
		public static const RULE_DESCENSION:String = 'RULE_DESCENSION';
		public static const RULE_ELEMENTAL:String = 'RULE_ELEMENTAL';
		public static const RULE_SWAP:String = 'RULE_SWAP';
		public static const RULE_ROULETTE:String = 'RULE_ROULETTE';
		
		public function tripleTriadRules() {
		
		}
		
		public static function roulette(mode:String = 'ff14_', gameRules:Object = null):Object {
			if (!gameRules) {
				gameRules = {
					OPEN_RULE:tripleTriadRules.RULE_DEFAULT_OPEN,
					SUDDEN_DEATH:false,
					RANDOM:false,
					ORDER:tripleTriadRules.RULE_DEFAULT_ORDER,
					REVERSE:false,
					FALLEN_ACE:false,
					SAME:false,
					SAME_WALL:false,
					PLUS:false,
					TYPE_RULE:tripleTriadRules.RULE_DEFAULT_TYPE,
					SWAP:false,
					ROULETTE:true
				};
			}
			var iterations:uint = 1+tools.rand(2);// at least one rule
			var possibleRules:Array;
			if (mode == 'ff14_') {
				possibleRules = [tripleTriadRules.RULE_ALL_OPEN, tripleTriadRules.RULE_ASCENSION, tripleTriadRules.RULE_CHAOS, tripleTriadRules.RULE_DESCENSION, tripleTriadRules.RULE_FALLEN_ACE, tripleTriadRules.RULE_ORDER, tripleTriadRules.RULE_PLUS, tripleTriadRules.RULE_RANDOM, tripleTriadRules.RULE_REVERSE, tripleTriadRules.RULE_SAME, tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_SWAP, tripleTriadRules.RULE_THREE_OPEN];
			} else if (mode == 'ff8_') {
				possibleRules = [tripleTriadRules.RULE_ALL_OPEN, tripleTriadRules.RULE_ELEMENTAL, tripleTriadRules.RULE_PLUS, tripleTriadRules.RULE_RANDOM, tripleTriadRules.RULE_SAME, tripleTriadRules.RULE_SAME_WALL, tripleTriadRules.RULE_SUDDEN_DEATH, tripleTriadRules.RULE_THREE_OPEN];
			}
			
			for (var i:uint = 0; i < iterations ; i++ ) {
				var rule:String = possibleRules[tools.rand(possibleRules.length - 1)];
				switch (rule) {
					case (tripleTriadRules.RULE_ALL_OPEN):
						gameRules.OPEN_RULE = rule;
						break;
					case (tripleTriadRules.RULE_THREE_OPEN):
						gameRules.OPEN_RULE = rule;
						break;
					case (tripleTriadRules.RULE_SUDDEN_DEATH):
						gameRules.SUDDEN_DEATH = true;
						break;
					case (tripleTriadRules.RULE_RANDOM):
						gameRules.RANDOM = true;
						break;
					case (tripleTriadRules.RULE_ORDER):
						gameRules.ORDER = rule;
						break;
					case (tripleTriadRules.RULE_CHAOS):
						gameRules.ORDER = rule;
						break;
					case (tripleTriadRules.RULE_REVERSE):
						gameRules.REVERSE = true;
						break;
					case (tripleTriadRules.RULE_FALLEN_ACE):
						gameRules.FALLEN_ACE = true;
						break;
					case (tripleTriadRules.RULE_SAME):
						gameRules.SAME = true;
						break;
					case (tripleTriadRules.RULE_SAME_WALL):
						gameRules.SAME_WALL = true;
						break;
					case (tripleTriadRules.RULE_PLUS):
						gameRules.PLUS = true;
						break;
					case (tripleTriadRules.RULE_ASCENSION):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_DESCENSION):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_ELEMENTAL):
						gameRules.TYPE_RULE = rule;
						break;
					case (tripleTriadRules.RULE_SWAP):
						gameRules.SWAP = true;
						break;
				}
			}
			
			
			return gameRules;
		}
	}
}