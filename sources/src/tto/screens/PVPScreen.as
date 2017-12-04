package tto.screens {
	import feathers.controls.Button;
	import feathers.controls.GroupedList;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.List;
	import feathers.controls.Panel;
	import feathers.controls.PickerList;
	import feathers.controls.Screen;
	import feathers.controls.ToggleSwitch;
	import feathers.data.HierarchicalCollection;
	import feathers.data.ListCollection;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.events.Event;
	import starling.textures.Texture;
	import tto.datas.Save;
	import tto.datas.tripleTriadRules;
	import tto.display.UserBar;
	import tto.Game;
	import tto.net.Socket;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class PVPScreen extends Screen {
		private var userBar:UserBar;
		private var mainPanel:Panel;
		public static var connectBtn:Button;
		private var matchesList:List;
		public static var matchesList:List;
		private var usersList:List;
		public static var usersList:List;
		private var freeModeSettings:GroupedList;
		private var openRulePicker:PickerList;
		private var orderRulePicker:PickerList;
		private var typeRulePicker:PickerList;
		private var suddenDeathToggle:ToggleSwitch;
		private var randomToggle:ToggleSwitch;
		private var reverseToggle:ToggleSwitch;
		private var fallenAceToggle:ToggleSwitch;
		private var sameToggle:ToggleSwitch;
		private var sameWallToggle:ToggleSwitch;
		private var plusToggle:ToggleSwitch;
		private var swapToggle:ToggleSwitch;
		private var rouletteToggle:ToggleSwitch;
		public static var registerBtn:Button;
		//private var sock:Socket;
		private var pingLabel:Label;
		private var acceptDefyBtn:Button;
		private var refreshBtn:Button;
		private var backButton:starling.display.Button;
		public static var pingLabel:Label;
		
		public function PVPScreen() {
			super();
		}
		
		override protected function draw():void {
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			mainPanel.x = 8
			mainPanel.y = 96
			mainPanel.width = stage.width - 16;
			mainPanel.height = 800 - 96 - 64;
			usersList.x = matchesList.x + matchesList.width + 8
			freeModeSettings.x = mainPanel.width - freeModeSettings.width - 32;
			freeModeSettings.height = mainPanel.height - 128;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			//sock = new Socket();
			
			userBar = new UserBar('PVP_SCREEN');
			addChild(userBar);
			
			connectBtn = new Button();
			if (Socket.connected) {
				connectBtn.label = i18n.gettext("STR_DISCONNECT");
				PVPScreen.refreshMatchesList();
				PVPScreen.refreshUsersList();
			} else
				connectBtn.label = i18n.gettext("STR_CONNECT");
			connectBtn.addEventListener(Event.TRIGGERED, handleConnect);
			
			refreshBtn = new Button();
			refreshBtn.label = i18n.gettext("STR_REFRESH");
			refreshBtn.addEventListener(Event.TRIGGERED, refreshBtnHandler);
			
			pingLabel = new Label();
			PVPScreen.pingLabel = pingLabel;
			
			mainPanel = new Panel();
			mainPanel.title = i18n.gettext('STR_MULTIPLAYER');
			mainPanel.headerFactory = headerFactory;
			mainPanel.footerFactory = panelFooterFactory;
			mainPanel.verticalScrollPolicy = Panel.SCROLL_POLICY_OFF;
			mainPanel.horizontalScrollPolicy = Panel.SCROLL_POLICY_OFF;
			addChild(mainPanel);
			
			matchesList = new List();
			matchesList.width = 300;
			matchesList.dataProvider = new ListCollection();
			matchesList.addEventListener(Event.CHANGE, matchesListHandler);
			mainPanel.addChild(matchesList);
			PVPScreen.matchesList = this.matchesList;
			
			usersList = new List();
			usersList.width = 300;
			usersList.dataProvider = new ListCollection();
			mainPanel.addChild(usersList);
			PVPScreen.usersList = this.usersList;
			
			freeModeSettings = new GroupedList();
			openRulePicker = new PickerList();
			openRulePicker.dataProvider = new ListCollection([{label: i18n.gettext(tripleTriadRules.RULE_DEFAULT_OPEN), code: tripleTriadRules.RULE_DEFAULT_OPEN}, {label: i18n.gettext(tripleTriadRules.RULE_THREE_OPEN), code: tripleTriadRules.RULE_THREE_OPEN}, {label: i18n.gettext(tripleTriadRules.RULE_ALL_OPEN), code: tripleTriadRules.RULE_ALL_OPEN}]);
			orderRulePicker = new PickerList();
			orderRulePicker.dataProvider = new ListCollection([{label: i18n.gettext(tripleTriadRules.RULE_DEFAULT_ORDER), code: tripleTriadRules.RULE_DEFAULT_ORDER}, {label: i18n.gettext(tripleTriadRules.RULE_ORDER), code: tripleTriadRules.RULE_ORDER}, {label: i18n.gettext(tripleTriadRules.RULE_CHAOS), code: tripleTriadRules.RULE_CHAOS}]);
			typeRulePicker = new PickerList();
			typeRulePicker.dataProvider = new ListCollection([{label: i18n.gettext(tripleTriadRules.RULE_DEFAULT_TYPE), code: tripleTriadRules.RULE_DEFAULT_TYPE}, {label: i18n.gettext(tripleTriadRules.RULE_ASCENSION), code: tripleTriadRules.RULE_ASCENSION}, {label: i18n.gettext(tripleTriadRules.RULE_DESCENSION), code: tripleTriadRules.RULE_DESCENSION}, {label: i18n.gettext(tripleTriadRules.RULE_ELEMENTAL), code: tripleTriadRules.RULE_ELEMENTAL}]);
			suddenDeathToggle = new ToggleSwitch();
			randomToggle = new ToggleSwitch();
			reverseToggle = new ToggleSwitch();
			fallenAceToggle = new ToggleSwitch();
			sameToggle = new ToggleSwitch();
			sameWallToggle = new ToggleSwitch();
			plusToggle = new ToggleSwitch();
			swapToggle = new ToggleSwitch();
			rouletteToggle = new ToggleSwitch();
			
			freeModeSettings.dataProvider = new HierarchicalCollection([{header: i18n.gettext('STR_RULES'), children: [{label: i18n.gettext(tripleTriadRules.RULE_OPEN), accessory: openRulePicker}, {label: i18n.gettext(tripleTriadRules.RULE_SUDDEN_DEATH), accessory: suddenDeathToggle}, {label: i18n.gettext(tripleTriadRules.RULE_RANDOM), accessory: randomToggle}, {label: i18n.gettext(tripleTriadRules.RULE_ORDER), accessory: orderRulePicker}, {label: i18n.gettext(tripleTriadRules.RULE_REVERSE), accessory: reverseToggle}, {label: i18n.gettext(tripleTriadRules.RULE_FALLEN_ACE), accessory: fallenAceToggle}, {label: i18n.gettext(tripleTriadRules.RULE_SAME), accessory: sameToggle}, {label: i18n.gettext(tripleTriadRules.RULE_SAME_WALL), accessory: sameWallToggle}, {label: i18n.gettext(tripleTriadRules.RULE_PLUS), accessory: plusToggle}, {label: i18n.gettext(tripleTriadRules.RULE_TYPE), accessory: typeRulePicker}, {label: i18n.gettext(tripleTriadRules.RULE_SWAP), accessory: swapToggle}, {label: i18n.gettext(tripleTriadRules.RULE_ROULETTE), accessory: rouletteToggle},]}]);
			mainPanel.addChild(freeModeSettings);
			
			registerBtn = new Button();
			registerBtn.isEnabled = false;
			registerBtn.label = i18n.gettext('STR_REGISTER_MATCH');
			registerBtn.addEventListener(Event.TRIGGERED, registerBtnHandler);
			
			acceptDefyBtn = new Button();
			acceptDefyBtn.isEnabled = false;
			acceptDefyBtn.label = i18n.gettext('STR_ACCEPT_DEFY');
			acceptDefyBtn.addEventListener(Event.TRIGGERED, acceptDefyBtnHandler);
		}
		
		private function refreshBtnHandler(e:Event):void {
			if (Socket.connected) {
				PVPScreen.refreshMatchesList();
				PVPScreen.refreshUsersList();
			}
		}
		
		private function acceptDefyBtnHandler(e:Event):void {
			acceptDefyBtn.isEnabled = false;
			var _ID:String = matchesList.selectedItem.params.id;
			prepareMatch(_ID);
			
			// on annule les parties si on est l'hote
			for (var id:String in Game.MATCHES) {
				if (String(Game.MATCHES[id].HOST) == String(Game.PROFILE_DATAS.USERNAME)) {
					if (int(Game.MATCHES[id].gils) > 0) {
						// si il y avait une mise l'hote doit retrouver ses gils
						Game.PROFILE_DATAS.MGP += Game.MATCHES[id].gils
					}
					Socket.send('<leave room="' + id + '" />');
					Socket.send('<cancel toroom="' + Socket.main_room + '" ><game_id>' + id + '</game_id></cancel>');
					delete Game.MATCHES[id];
					PVPScreen.refreshMatchesList();
				}
			}
		}
		
		public function prepareMatch(_ID:String):void {
			var match:Object = Game.MATCHES[_ID];
			var test:Boolean = true;
			if (Game.PROFILE_DATAS.CARDS.length < 5)
				test = false;
			if (Game.PROFILE_DATAS.MGP < int(match.gils))
				test = false;
			if (int(match.statut) == 2)
				test = false;
			if (test) {
				if (String(match.opponent) == 'STR_FREE') {
					// si la partie est en free on demande à l'hote la permission de venir pour éviter les doubles join
					Socket.send('<can_join toclient="' + String(match.host) + '" ><id>' + match.id + '</id></can_join>');
				} else {
					match.statut = 2; //'Running'
					match.opponent = Game.PROFILE_DATAS.USERNAME;
					match.opponent_id = Game.PROFILE_DATAS.CREATION_DATE;
					match.opponent_rank = Game.PROFILE_DATAS.RANK;
					match.opponent_avatar = Game.PROFILE_DATAS.AVATAR_ID;
					
					var reponse:String = '<actu_game toroom="' + Socket.main_room + '">';
					for (var key:String in match) {
						reponse += '<' + key + '>' + match[key] + '</' + key + '>';
					}
					reponse += '</actu_game>';
					Socket.send(reponse);
					Socket.send('<join room="' + _ID + '" />');
					Socket.send('<start_game toroom="' + _ID + '"/>');
					
					PVPScreen.refreshMatchesList();
					
					Game.PROFILE_DATAS.STARTED_MATCHES += 1;
					Game.PROFILE_DATAS.PVP_MATCHES += 1;
					// s'il y a une mise on décremente les gils
					if (int(match.gils) > 0)
						Game.PROFILE_DATAS.MGP -= int(match.gils);
					Save.save(Game.PROFILE_DATAS);
					
					var gameRules:Object = {OPEN_RULE: tripleTriadRules.RULE_DEFAULT_OPEN, SUDDEN_DEATH: (uint(match.ms)) ? true : false, RANDOM: (uint(match.random)) ? true : false, ORDER: tripleTriadRules.RULE_DEFAULT_ORDER, REVERSE: (uint(match.reverse)) ? true : false, FALLEN_ACE: (uint(match.fallen_ace)) ? true : false, SAME: (uint(match.same)) ? true : false, SAME_WALL: (uint(match.samewall)) ? true : false, PLUS: (uint(match.plus)) ? true : false, TYPE_RULE: tripleTriadRules.RULE_DEFAULT_TYPE, SWAP: (uint(match.swap)) ? true : false, ROULETTE: (uint(match.roulette)) ? true : false};
					if (uint(match.open))
						gameRules.OPEN_RULE = tripleTriadRules.RULE_ALL_OPEN;
					if (uint(match.element))
						gameRules.TYPE_RULE = tripleTriadRules.RULE_ELEMENTAL;
					this.dispatchEventWith('pvp_free_mode', false, {ID: _ID, HOST: false, RULES: gameRules, BLUE_PLAYER_NAME: Game.PROFILE_DATAS.USERNAME, BLUE_CARDS: [], RED_CARDS: [], RED_PLAYER_NAME: String(match.host)});
				}
			}
		}
		
		private function matchesListHandler(e:Event):void {
			if (matchesList.selectedItem && ((matchesList.selectedItem.params.opponent == Game.PROFILE_DATAS.USERNAME) || (matchesList.selectedItem.params.opponent == 'STR_FREE'))) {
				acceptDefyBtn.isEnabled = true;
			}
		}
		
		private function registerBtnHandler(e:Event):void {
			var randomizer:Array, randomCards:Array, i:uint, rand:uint;
			var gameRules:Object = {OPEN_RULE: openRulePicker.selectedItem.code, SUDDEN_DEATH: suddenDeathToggle.isSelected, RANDOM: randomToggle.isSelected, ORDER: orderRulePicker.selectedItem.code, REVERSE: reverseToggle.isSelected, FALLEN_ACE: fallenAceToggle.isSelected, SAME: sameToggle.isSelected, SAME_WALL: sameWallToggle.isSelected, PLUS: plusToggle.isSelected, TYPE_RULE: typeRulePicker.selectedItem.code, SWAP: swapToggle.isSelected, ROULETTE: rouletteToggle.isSelected};
			
			var userCards:Array = [];
			
			var test:Boolean = true;
			var message:String = '';
			
			if (Game.PROFILE_DATAS.CARDS.length < 5)
				test = false;
			if (test) {
				var now:Date = new Date();
				var new_game:Object = new Object();
				var meta:* = now.getTime() + '00' + Math.round(Math.random() * 10000);
				new_game.id = 'game_' + uint(meta).toString(16); // le nom de la room doit être absolument unique
				new_game.host = Game.PROFILE_DATAS.USERNAME;
				new_game.host_id = Game.PROFILE_DATAS.CREATION_DATE;
				new_game.host_ip = '192.168.0.1';
				new_game.host_rank = Game.PROFILE_DATAS.RANK;
				new_game.host_avatar = Game.PROFILE_DATAS.AVATAR_ID
				new_game.statut = 1; //'waiting'
				new_game.opponent = 'STR_FREE';
				//home.socket.send('<gdef toclient="'+new_game.opponent+'"><id>'+new_game.id+'</id></gdef>');
				new_game.join = 0;
				new_game.open = (gameRules.OPEN_RULE == tripleTriadRules.RULE_ALL_OPEN) ? 1 : 0;
				new_game.three_open = (gameRules.OPEN_RULE == tripleTriadRules.RULE_THREE_OPEN) ? 1 : 0;
				new_game.same = (gameRules.SAME) ? 1 : 0;
				new_game.random = (gameRules.RANDOM) ? 1 : 0;
				new_game.reverse = (gameRules.REVERSE) ? 1 : 0;
				new_game.swap = (gameRules.SWAP) ? 1 : 0;
				new_game.order = (gameRules.ORDER == tripleTriadRules.RULE_ORDER) ? 1 : 0;
				new_game.chaos = (gameRules.ORDER == tripleTriadRules.RULE_CHAOS) ? 1 : 0;
				new_game.roulette = (gameRules.ROULETTE) ? 1 : 0;
				new_game.fallen_ace = (gameRules.FALLEN_ACE) ? 1 : 0;
				new_game.plus = (gameRules.PLUS) ? 1 : 0;
				new_game.element = (gameRules.TYPE_RULE == tripleTriadRules.RULE_ELEMENTAL) ? 1 : 0;
				new_game.bonus = (gameRules.TYPE == tripleTriadRules.RULE_ASCENSION) ? 1 : 0;
				new_game.malus = (gameRules.TYPE == tripleTriadRules.RULE_DESCENSION) ? 1 : 0;
				new_game.samewall = (gameRules.SAME_WALL) ? 1 : 0;
				new_game.ms = (gameRules.SUDDEN_DEATH) ? 1 : 0;
				new_game.gils = 0;
				new_game.card = 'No';
				new_game.timer = 30;
				
				Game.MATCHES[new_game.id] = new_game;
				
				var req:String = '<new_game toroom="' + Socket.main_room + '">';
				for (var item:String in new_game)
					req += '<' + item + '>' + new_game[item] + '</' + item + '>';
				req += '</new_game>';
				Socket.send(req);
				Socket.send('<addchild parentroom="' + Socket.main_room + '" childroom="' + new_game.id + '" />');
				
				PVPScreen.refreshMatchesList();
			}
		}
		
		private function headerFactory():Header {
			var header:Header = new Header();
			backButton = new starling.display.Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			header.leftItems = new <DisplayObject>[backButton];
			return header;
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		private function panelFooterFactory():Header {
			var panHeader:Header = new Header();
			panHeader.leftItems = new <DisplayObject>[connectBtn, refreshBtn, pingLabel];
			panHeader.rightItems = new <DisplayObject>[acceptDefyBtn, registerBtn];
			return panHeader;
		}
		
		protected function handleConnect(event:Event):void {
			if (!Socket.connected) {
				//nom="Esthar" IP="triple-triad-online.com" port="2468" langue="FR"
				//this.sock.connect({ip: "triple-triad-online.com", port: "2468"});
				Socket.connect({ip: "localhost", port: "3000"});
				//connectBtn.label = i18n.gettext("STR_DISCONNECT");
				//registerBtn.isEnabled = true;
			} else {
				Socket.close();
				connectBtn.label = i18n.gettext("STR_CONNECT");
				registerBtn.isEnabled = false;
			}
		}
		
		public static function refreshUsersList(list:Array = null):void {
			if (!list)
				list = Game.USERS;
			for each (var user:Object in list) {
				var iconTex:Texture = Assets.manager.getTexture(user.params.iconID);
				var userIcon:Image = (iconTex) ? new Image(iconTex) : new Image(Assets.manager.getTexture('ffxiv_twi02001'))
				userIcon.width = userIcon.height = 25
				user.icon = userIcon;
			}
			usersList.dataProvider = new ListCollection(list);
			usersList.x = matchesList.x + matchesList.width + 8
		}
		
		public static function refreshMatchesList():void {
			var list:Array = []
			for (var id:String in Game.MATCHES) {
				var match:Object = Game.MATCHES[id];
				if (String(match.host).length > 0) {
					var host:String = match.host;
					var opponent:String = match.opponent;
					var label:String = (opponent !== '' && opponent !== null) ? host + ' vs ' + opponent : 'match of ' + host
					list.push({label: label, params: match})
				}
			}
			matchesList.dataProvider = new ListCollection(list);
			usersList.x = matchesList.x + matchesList.width + 8
		}
		
		override public function dispose():void {
			connectBtn.removeEventListener(Event.TRIGGERED, handleConnect);
			refreshBtn.removeEventListener(Event.TRIGGERED, refreshBtnHandler);
			matchesList.removeEventListener(Event.CHANGE, matchesListHandler);
			registerBtn.removeEventListener(Event.TRIGGERED, registerBtnHandler);
			acceptDefyBtn.removeEventListener(Event.TRIGGERED, acceptDefyBtnHandler);
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			super.dispose();
		}
	}
}