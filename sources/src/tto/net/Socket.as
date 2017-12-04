package tto.net {
	import feathers.controls.Alert;
	import feathers.data.ListCollection;
	import flash.events.DataEvent;
	import flash.events.Event;
	import flash.events.IEventDispatcher;
	import flash.events.IOErrorEvent;
	import flash.events.ProgressEvent;
	import flash.events.SecurityErrorEvent;
	import flash.net.XMLSocket;
	import flash.system.Security;
	import flash.utils.clearInterval;
	import flash.utils.getTimer;
	import flash.utils.setInterval;
	import tto.Game;
	import tto.datas.Save;
	import tto.datas.tripleTriadRules;
	import tto.display.Card;
	import tto.display.Tile;
	import tto.screens.BaseMatchScreen;
	import tto.screens.PVPScreen;
	import tto.screens.playerPanel;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class Socket {
		public static var main_room:String = 'Gold Saucer';
		private static const pingDelay:int = 1000;
		
		private static var pingInt:int = 0;
		private static var ping:int = 0;
		private static var _isInit:Boolean = false;
		private static var _connected:Boolean = false;
		private static var _usersList:Array;
		//private static var socketio:SocketIo;
		private static var socket:XMLSocket;
		
		public function Socket() {
		
		}
		
		public static function get connected():Boolean {
			return _connected;
		}
		
		public static function connect(param:Object):void {
			// param = {ip:"", port:""}
			
			trace('Socket connect : ' + param.ip + ':' + param.port);
			
			if (Game.PROFILE_DATAS.MODE == 'ff8_')
				Socket.main_room = 'Salon';
			if (!_connected) {
				Security.loadPolicyFile("xmlsocket://" + param.ip + ":" + param.port);
				socket = new XMLSocket(param.ip, param.port);
				
				socket.addEventListener(Event.CONNECT, onConnect);
				socket.addEventListener(Event.CLOSE, closeHandler);
				socket.addEventListener(DataEvent.DATA, dataHandler);
				socket.addEventListener(IOErrorEvent.IO_ERROR, ioErrorHandler);
				socket.addEventListener(ProgressEvent.PROGRESS, progressHandler);
				socket.addEventListener(SecurityErrorEvent.SECURITY_ERROR, securityErrorHandler);
			}
		}
		
		public static function send(value:Object):void {
			if (_connected) {
				if (value !== '{"action":"ping"}') trace("Socket send : " + value);
				Socket.socket.send(value);
			}
		}
		
		public static function close():void {
			if (Socket._connected) {
				var infos:Object = new Object();
				infos.action = "exit";
				Socket.send(JSON.stringify(infos));
				Socket.socket.close();
				Socket._connected = false;
				clearInterval(pingInt);
				_connected = false;
				Game.MATCHES = new Object();
				PVPScreen.pingLabel.text = '';
				PVPScreen.refreshMatchesList();
				PVPScreen.refreshUsersList([]);
				PVPScreen.connectBtn.label = i18n.gettext("STR_CONNECT");
				PVPScreen.registerBtn.isEnabled = false;
			}
		}
		
		private static function onConnect(e:Event):void {
			trace("Socket onConnect");
			if (socket.connected) {
				Socket._connected = true;
				PVPScreen.connectBtn.label = i18n.gettext("STR_DISCONNECT");
				PVPScreen.registerBtn.isEnabled = true;
				
				var infos:Object = new Object();
				infos.action = "incoming";
				infos.nickname = Game.PROFILE_DATAS.USERNAME;
				infos.params = {iconID:"ffxiv_twi02001"};
				infos.params.iconID = Game.PROFILE_DATAS.AVATAR_ID;
				if (Game.PROFILE_DATAS.ADMIN) infos.isAdmin = true;
				Socket.send(JSON.stringify(infos));
				
				pingInt = setInterval(sendPing, pingDelay);
			}
		}
		
		private static function securityErrorHandler(e:SecurityErrorEvent):void {
			trace("securityErrorHandler: " + e)
		}
		
		private static function progressHandler(e:ProgressEvent):void {
			trace("progressHandler loaded:" + e.bytesLoaded + " total: " + e.bytesTotal);
		}
		
		private static function ioErrorHandler(e:IOErrorEvent):void {
			trace("ioErrorHandler: " + e);
			
			Alert.show(i18n.gettext("STR_CONNECTION_ERROR"), i18n.gettext("STR_ERROR"), new ListCollection([{label: i18n.gettext('STR_OK')}]));
		}
		
		private static function sendPing():void {
			ping = getTimer();
			var infos:Object = new Object();
			infos.action = "ping";
			Socket.send(JSON.stringify(infos));
		}
		
		private static function dataHandler(e:DataEvent):void {
			trace('dataHandler : ' + e.data)
			if (e.data == 'pong') {
				Socket_On_pong();
			}
			var sj:Object
			try {
				sj = JSON.parse(e.data);
			} catch (e:Error) {
				sj = null;
			}
			
			if (sj) {
				if (sj.users) {
					Socket_On_clients(sj.users as Array);
				}
			}
		}
		
		private static function closeHandler(e:Event):void {
			trace('closeHandler')
			Socket.close();
		}
		
		// PALABRE INSTRUCTIONS
		
		private static function Socket_On_pong():void {
			PVPScreen.pingLabel.text = ('Gold Saucer : ' + String(getTimer() - ping) + "ms");
		/*
		   PVPScreen.refreshMatchesList();
		   PVPScreen.refreshUsersList();
		 */
		}
		
		private function Socket_On_error(node:XML):void {
			trace('Socket_On_error : ' + node)
		}
		
		private function Socket_On_joined(node:XML):void {
			var ROOM:String = String(node.attribute('room'));
			if (ROOM == main_room) {
				// si on rejoins la main_room, on viens d'arriver sur le serveur.
				//if (String(USER.guild)!=='') socket.send('<join room="'+USER.guild+'" />');
			} else {
				// sinon c'est la room de guild ou une partie.
			}
		}
		
		private function Socket_On_leaved(node:XML):void {
			//<leaved room="game_1270307634746"/>
			// lorsqu'on quitte un canal, on kill le chat correspondant
			var room_name:String = node.attribute('room');
		}
		
		private function Socket_On_childrooms(node:XML):void {
			// les childrooms de la main_room correspondent à des parties.
			// distribué à toute la main_room dès qu'une childroom est créée.
			/*
			   <childrooms name="Salon">
			   <room name="game_1270803924785" clients="1" parent="Salon">
			   <param name="openParams" value="0"/>
			   <param name="title" value=""/>
			   </room>
			   </childrooms>
			 */
			
			var ROOM:String = String(node.attribute('name'));
			if (ROOM == main_room) {
				var existingRooms:Array = new Array();
				for each (var room:XML in node.child('room')) {
					var room_name:String = room.attribute('name');
					if (Game.MATCHES[room_name] !== undefined) {
						existingRooms.push(room_name);
					} else {
						// on a pas les infos sur cette parties, il faut les demander.
						//socket.send('<get_game_infos toroom="' + main_room + '" ><id>' + room_name + '</id></get_game_infos>');
					}
				}
				// on purge éventuellement l'objet
				for (var id:String in Game.MATCHES) {
					if (existingRooms.indexOf(id) == -1) {
						delete Game.MATCHES[id];
					}
				}
					// il faut refresh la liste des parties
			}
		}
		
		private function Socket_On_room(node:XML):void {
			//donne les param et le nombre d'users de la room après join.
			/*
			   <room name="Salon" clients="1">
			   <param name="openParams" value="0"/>
			   <param name="title" value=""/>
			   </room>
			 */
			var room_name:String = node.attribute('name');
		/*
		   if (! chat_channels[room_name]) {
		   chat_channels.push(room_name);
		   var formated:String = '<font color="#666666"><b>&lt;System&gt; : </b>'+String(LANGLIB['STR_CHANEL_WELCOME']).replace('$r', room_name)+'</font>';
		   chat_messages.push(formated);
		   }
		 */
		}
		
		private function Socket_On_rooms(node:XML):void {
			//trace('Socket_On_rooms : '+node.toXMLString())
			var room_length:int = node.child('room').length();
			for (var i:int = 0; i < room_length; i++) {
				var room:* = node.child('room')[i];
				if (String(room.attribute('name')) == String(main_room)) {
					
				}
			}
			//socket.send('<clientparam name="ip" value="' + home.USER.ip + '"/>');
		/*
		   if (home.USER.guild) {
		   home.socket.send('<clientparam name="guild" value="'+home.USER.guild+'"/>');
		   }
		 */
		/*
		   socket.send('<clientparam name="group" value="1"/>');
		   socket.send('<clientparam name="tag" value="V4"/>');
		   socket.send('<clientparam name="iconID" value="' + Game.PROFILE_DATAS.AVATAR_ID + '"/>');
		   socket.send('<clientparam name="AFK" value="0"/>');
		   socket.send('<join room="' + main_room + '" />');
		 */
		}
		
		private static function Socket_On_clients(clients:Array):void {
			trace('Socket_On_clients : ' + clients);
			_usersList = clients;
			Game.USERS = _usersList;
			PVPScreen.refreshUsersList();
		}
		
		private function Socket_On_client(node:XML):void {
			//indique lorsqu'un user join ou quit une room.
			//<client name="Sylvain" left="test"/>
			var user:String = node.attribute('name');
			var join:Boolean = (String(node.attribute('joined')) !== '') ? true : false;
			var room:String = (join) ? node.attribute('joined') : node.attribute('left');
			
			var now:Date = new Date();
			// on informe la room
			if (join) {
				var formated:String = '<font color="#666666"><b>&lt;System&gt; : </b>' + user + ' joined channel.</font>';
			} else {
				formated = '<font color="#666666"><b>&lt;System&gt; : </b>' + user + ' left channel.</font>';
			}
			// On entre le message dans le tableau
		/*
		   var message_length:int=chat_messages.length;
		   if (message_length>49) {
		   chat_messages.reverse();
		   chat_messages.pop();
		   chat_messages.reverse();
		   }
		   chat_messages.push(formated);
		   panel_chat.actu();
		 */
		}
		
		private function Socket_On_clientparam(node:XML):void {
			//<clientparam r="Salon" name="AFK" value="true" f="Mao"/>
			var data_length:int = _usersList.length;
			for (var i:int = 0; i < data_length; i++) {
				var datas:Object = _usersList[i];
				if (datas.label == node.attribute('f')) {
					_usersList[i].params[node.attribute('name')] = node.attribute('value');
					break;
				}
			}
			//panel_chat.build_users_list();
		}
		
		private function Socket_On_m(node:XML):void {
			//<m r="room" f="user">message</m>
			var chan:String = node.attribute('r');
			var from:String = node.attribute('f');
			var now:Date = new Date();
			if (chan !== '') {
				var color:String = '333333';
				/*
				   if (chan==USER.guild) color = panel_chat.colors['GUILD'];
				   else color = '333333';
				 */
				var formated:String = '<font color="#' + color + '"><b>&lt;' + chan + '&gt;&lt;' + from + '&gt; : </b></font>' + node;
			} else {
				formated = '<font color="#0099CC"><b>&lt;PM to you&gt;&lt;' + from + '&gt; : </b></font>' + node;
			}
		/*
		   var message_length:int=chat_messages.length;
		   if (message_length>49) {
		   chat_messages.reverse();
		   chat_messages.pop();
		   chat_messages.reverse();
		   }
		   chat_messages.push(formated);
		   panel_chat.actu();
		   var chatBt:* = menu_mc.getChildByName('bt_chat');
		   if (!panel_chat.visible) chatBt.hl.visible = true;
		 */
		}
		
		//  FUNCTION SOCKET CUSTOM  //----------------------------------------------------------------------------------------
		private function Socket_On_invitation(node:XML):void {
			// GUILD_INVIT
		/*
		   var config:Object = new Object();
		   var infos_len:int=node.elements().length();
		   for (var i:int = 0; i<infos_len; i++) config[node.elements()[i].name()]=node.elements()[i];
		   config.from=String(node.attribute('f'));
		   panel_guild.INVITS.push(config);
		   message_box.caution(LANGLIB['STR_INFO'], LANGLIB['STR_GUILD_INVIT_NOTIF']);
		 */
		}
		
		private function Socket_On_new_game(node:XML):void {
			// function permettant d'informer de la création d'une nouvelle partie
			
			var new_game:Object = new Object();
			for each (var child:XML in node.elements())
				new_game[String(child.name())] = child.toString();
			Game.MATCHES[new_game.id] = new_game;
			if (String(new_game.opponent) == String(Game.PROFILE_DATAS.USERNAME)) {
				// on lance une invite si je suis l'adversaire
			}
			PVPScreen.refreshMatchesList();
		}
		
		private function Socket_On_get_game_infos(node:XML):void {
			// quelqu'un demande les infos d'une partie
			var from:String = node.attribute('f');
			var id:String = node.child('id');
			if (Game.MATCHES[id] !== undefined && String(Game.MATCHES[id].host) == String(Game.PROFILE_DATAS.USERNAME)) {
				// la partie existe et je suis l'hote
				var req:String = '<actu_game toclient="' + from + '">';
				for (var item:String in Game.MATCHES[id])
					req += '<' + item + '>' + Game.MATCHES[id][item] + '</' + item + '>';
				req += '</actu_game>';
					//socket.send(req);
			}
		}
		
		// home.socket.send('<decline_game toclient="'+home.GAMES[_ID].host+'"><id>'+_ID+'</id></decline_game>');
		private function Socket_On_decline_game(node:XML):void {
			// on refuse un des mes défis.
			var id:String = node.child('id');
			var cancelable:Boolean = false;
			if (Game.MATCHES[id] !== undefined && String(Game.MATCHES[id].host) == String(Game.PROFILE_DATAS.USERNAME) && int(Game.MATCHES[id].statut) == 1) {
				// Si les id correspond il faut que l'user soit l'hote et que la partie ne soit pas en cours
				cancelable = true;
			}
			if (cancelable) {
				// si la partie est annulable
				if (int(Game.MATCHES[id].gils) > 0) {
					// si il y avait une mise l'hote doit retrouver ses gils
					Game.PROFILE_DATAS.MGP += Game.MATCHES[id].gils
				}
				/*
				   socket.send('<leave room="' + id + '" />');
				   socket.send('<cancel toroom="' + main_room + '" ><game_id>' + id + '</game_id></cancel>');
				 */
				delete Game.MATCHES[id];
				PVPScreen.refreshMatchesList();
			}
		}
		
		private function Socket_On_actu_game(node:XML):void {
			//trace('Socket_On_actu_game')
			// on cherche la partie dans la liste
			var id:String = node.child('id');
			if (Game.MATCHES[id]) {
				for each (var child:XML in node.elements())
					Game.MATCHES[id][child.name()] = child.toString();
				if (int(Game.MATCHES[id].statut) == 3) {
					// si la partie est terminée
					if (String(Game.MATCHES[id].host) !== String(Game.PROFILE_DATAS.USERNAME) && String(Game.MATCHES[id].opponent) !== String(Game.PROFILE_DATAS.USERNAME)) {
						// si je ne suis ni l'hote ni l'adversaire, j'enlève cette partie du tableau
						delete Game.MATCHES[id];
					}
				}
			} else {
				// on créé la partie
				var new_game:Object = new Object()
				for each (child in node.elements())
					new_game[child.name()] = child.toString();
				Game.MATCHES[id] = new_game;
			}
			
			PVPScreen.refreshMatchesList();
		}
		
		private function Socket_On_cancel(node:XML):void {
			var id:String = node.child('game_id');
			// si on est dans cette room pour une raison quelconque, il faut la quitter
			//socket.send('<leave room="' + id + '" />');
			// si kill les infos de la partie
			if (Game.MATCHES[id]) {
				if (String(Game.MATCHES[id].opponent) == String(Game.PROFILE_DATAS.USERNAME) && int(Game.MATCHES[id].statut) == 1) {
					// si j'étais l'adversaire de cette partie, on kill l'invitation si elle existe.
					delete Game.MATCHES[id];
				}
			}
			
			PVPScreen.refreshMatchesList();
		}
		
		private function Socket_On_can_join(node:XML):void {
			// un joueur veux savoir s'il peut rejoindre une partie
			//<can_join f="Cid"><id>game_175641616161</id></can_join>
			var from:String = node.attribute('f');
			var id:String = node.child('id').toString();
			// on cherche la partie dans la liste
			if (Game.MATCHES[id]) {
				if (Game.MATCHES[id].join == 0) {
					// personne n'est en train de rejoindre, il peut venir
					Game.MATCHES[id].join = int(Game.MATCHES[id].join) + 1;
						//socket.send('<plz_join toclient="' + from + '" ><id>' + id + '</id></plz_join>');
				} else {
					// des personnes sont en train de rejoindre
					//socket.send('<cannot_join toclient="' + from + '" ><id>' + id + '</id></cannot_join>');
				}
			} else {
				// si on ne trouve pas la partie le sender ne peut pas rejoindre
				//socket.send('<cannot_join toclient="' + from + '" ><id>' + id + '</id></cannot_join>');
			}
		}
		
		private function Socket_On_cannot_join(node:XML):void {
			//message_box.caution(LANGLIB['STR_CAUTION'], LANGLIB['STR_GAME_CANNOT_JOIN']);
		}
		
		private function Socket_On_plz_join(node:XML):void {
			// je peut rejoindre la partie FREE
			var host:String = node.attribute('f')
			var id:String = node.child('id').toString();
			// on cherche la partie dans la liste
			if (Game.MATCHES[id]) {
				var match:Object = Game.MATCHES[id];
				match.statut = 2; //'Running'
				match.opponent = Game.PROFILE_DATAS.USERNAME;
				match.opponent_id = Game.PROFILE_DATAS.CREATION_DATE;
				match.opponent_rank = Game.PROFILE_DATAS.RANK;
				match.opponent_avatar = Game.PROFILE_DATAS.AVATAR_ID;
				if (int(match.gils) > 0) {
					// s'il y a une mise on décremente les gils
					Game.PROFILE_DATAS.MGP -= Game.MATCHES[id].gils
				}
				// on envois les nouvelles données à tout le monde.
				var reponse:String = '<actu_game toroom="' + main_room + '">';
				for (var key:String in match)
					reponse += '<' + key + '>' + String(match[key]) + '</' + key + '>';
				reponse += '</actu_game>';
				/*
				   socket.send(reponse);
				   socket.send('<join room="' + id + '" />');
				   socket.send('<start_game toroom="' + id + '"/>');
				 */
				
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
				if (uint(match.three_open))
					gameRules.OPEN_RULE = tripleTriadRules.RULE_THREE_OPEN;
				if (uint(match.element))
					gameRules.TYPE_RULE = tripleTriadRules.RULE_ELEMENTAL;
				if (uint(match.malus))
					gameRules.TYPE_RULE = tripleTriadRules.RULE_DESCENSION;
				if (uint(match.bonus))
					gameRules.TYPE_RULE = tripleTriadRules.RULE_ASCENSION;
				if (uint(match.order))
					gameRules.ORDER = tripleTriadRules.RULE_ORDER;
				if (uint(match.chaos))
					gameRules.ORDER = tripleTriadRules.RULE_CHAOS;
				Game.prepareMatch({ID: id, HOST: false, RULES: gameRules, BLUE_PLAYER_NAME: Game.PROFILE_DATAS.USERNAME, BLUE_CARDS: [], RED_CARDS: [], RED_PLAYER_NAME: String(match.host)});
			} else {
				// la partie que vous tentez de rejoindre n'éxiste pas.
			}
		}
		
		private function Socket_On_start_game(node:XML):void {
			var id:String = node.attribute('r');
			// on cherche la partie dans la liste
			if (Game.MATCHES[id]) {
				// on lance la partie
				Game.PROFILE_DATAS.STARTED_MATCHES += 1;
				Game.PROFILE_DATAS.PVP_MATCHES += 1;
				Save.save(Game.PROFILE_DATAS);
				
				var match:Object = Game.MATCHES[id];
				var gameRules:Object = {OPEN_RULE: tripleTriadRules.RULE_DEFAULT_OPEN, SUDDEN_DEATH: (uint(match.ms)) ? true : false, RANDOM: (uint(match.random)) ? true : false, ORDER: tripleTriadRules.RULE_DEFAULT_ORDER, REVERSE: (uint(match.reverse)) ? true : false, FALLEN_ACE: (uint(match.fallen_ace)) ? true : false, SAME: (uint(match.same)) ? true : false, SAME_WALL: (uint(match.samewall)) ? true : false, PLUS: (uint(match.plus)) ? true : false, TYPE_RULE: tripleTriadRules.RULE_DEFAULT_TYPE, SWAP: (uint(match.swap)) ? true : false, ROULETTE: (uint(match.roulette)) ? true : false};
				if (uint(match.open))
					gameRules.OPEN_RULE = tripleTriadRules.RULE_ALL_OPEN;
				if (uint(match.three_open))
					gameRules.OPEN_RULE = tripleTriadRules.RULE_THREE_OPEN;
				if (uint(match.element))
					gameRules.TYPE_RULE = tripleTriadRules.RULE_ELEMENTAL;
				if (uint(match.malus))
					gameRules.TYPE_RULE = tripleTriadRules.RULE_DESCENSION;
				if (uint(match.bonus))
					gameRules.TYPE_RULE = tripleTriadRules.RULE_ASCENSION;
				if (uint(match.order))
					gameRules.ORDER = tripleTriadRules.RULE_ORDER;
				if (uint(match.chaos))
					gameRules.ORDER = tripleTriadRules.RULE_CHAOS;
				Game.prepareMatch({ID: id, HOST: true, RULES: gameRules, BLUE_PLAYER_NAME: Game.PROFILE_DATAS.USERNAME, BLUE_CARDS: [], RED_CARDS: [], RED_PLAYER_NAME: String(match.opponent)});
				
			} else {
				// la partie que vous tentez de commencer n'éxiste pas.
			}
		}
		
		private function Socket_On_ready(node:XML):void {
			var ID:String = String(node.attribute('r'));
			if (!Game.MATCHES[ID].screen.OPPONENT_READY) {
				Game.MATCHES[ID].screen.OPPONENT_READY = true
				if (!Game.MATCHES[ID].screen.HOST)
					//this.socket.send('<ready toroom="' + ID + '"/>');
					Game.MATCHES[ID].screen.deckSelectionPhase();
			}
		}
		
		private function Socket_On_setCards(node:XML):void {
			//var from:String=node.child('from');
			var ID:String = String(node.attribute('r'));
			Game.MATCHES[ID].screen.setOpponentCards([node.child("c1"), node.child("c2"), node.child("c3"), node.child("c4"), node.child("c5")]);
		}
		
		private function Socket_On_initiative(node:XML):void {
			var ID:String = String(node.attribute('r'));
			if (String(Game.PROFILE_DATAS.USERNAME) == String(node.toString())) {
				Game.MATCHES[ID].screen.setPof('blue');
			} else {
				Game.MATCHES[ID].screen.setPof('red');
			}
		}
		
		//Socket.send('<swap toroom="' + this.ID + '">' + '<blue><index>'+randRed.index+'</index><id>'+randBlue.id+'</id></blue><red><index>'+randBlue.index+'</index><id>'+randRed.id+'</id></red>' + '</swap>');
		private function Socket_On_swap(node:XML):void {
			var ID:String = String(node.attribute('r'));
			// TODO : {SWAP_CARDS[0].index, SWAP_CARDS[0].id}
			var blue:XMLList = node.child("blue")
			var blueIndex:uint = uint(blue.child('index').toString());
			var blueId:uint = uint(blue.child('id').toString());
			var red:XMLList = node.child("red")
			var redIndex:uint = uint(red.child('index').toString());
			var redId:uint = uint(red.child('id').toString());
			
			Game.MATCHES[ID].screen.setSwap([{index: blueIndex, id: blueId}, {index: redIndex, id: redId}]);
		}
		
		private function Socket_On_elements(node:XML):void {
			var ID:String = String(node.attribute('r'));
			// { earth:7, fire:2, holy:6, ice:4, lightning:3, poison:5, water:1, wind:8 }
			var legacyElements:Array = ['', 'water', 'fire', 'lightning', 'ice', 'poison', 'holy', 'earth', 'wind']
			var elem_length:int = node.elements().length();
			var elements:Array = []
			for (var i:int = 0; i < elem_length; i++) {
				var elementId:uint = uint(node.elements()[i])
				elements.push(legacyElements[elementId]);
			}
			Game.MATCHES[ID].screen.setElements(elements);
		}
		
		// home.socket.send('<cardMove toroom="'+game_config.id+'"><c>ply_0_c_1</c><p>p3</p></cardMove>');
		private function Socket_On_cardMove(node:XML):void {
			var ID:String = String(node.attribute('r'));
			var ttScreen:BaseMatchScreen = Game.MATCHES[ID].screen;
			var hash:Array = String(node.child("c")).split('_') // ply 0 c 1
			var cardIndex:uint = uint(hash[3])
			var player:playerPanel = (uint(hash[1]) == 1) ? ttScreen.bluePlayer : ttScreen.redPlayer
			var card:Card = player.getCardAt(cardIndex)
			var tileIndex:uint = uint(String(node.child("p")).charAt(1)) - 1;
			var tile:Tile = ttScreen.board.tiles[tileIndex];
			ttScreen.cardOnTile(card, tile);
		}
		
		private function Socket_On_tradeCards(node:XML):void {
			// fonction qui affiche le gain de carte en fin de partie
			// vision du perdant.
		/*
		   var id:String = node.attribute('r');
		   var board:* = game_boards[id];
		   var cards_length:int=node.elements().length();
		   var i:int=0;
		   board.gain_card_mc.value={i:0,length:cards_length};
		   while (i<cards_length) {
		   var card_i:* =board.gain_card_mc[String(node.elements()[i])];
		   card_i.addEventListener(MouseEvent.CLICK, board.reverse);
		   card_i.dispatchEvent(new MouseEvent(MouseEvent.CLICK));
		   card_i.removeEventListener(MouseEvent.CLICK, board.reverse);
		   board.gain_card_mc.setChildIndex(card_i, 12);
		   Tweener.addTween(card_i, {x:341, y:244, scaleX:1.5, scaleY:1.5, time:1, delay:i+0.6});
		   Tweener.addTween(card_i, {alpha:0, time:0.5, delay:i+1.6, onComplete:after_tweener(id)});
		   i++;
		   }
		 */
		}
		
		private function Socket_On_libActualise(node:XML):void {
			//load_user_('all');
		}
	}

}