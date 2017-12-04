package tto.screens {
	import feathers.controls.Button;
	import feathers.controls.GroupedList;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.Panel;
	import feathers.data.HierarchicalCollection;
	import feathers.layout.VerticalLayout;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.events.Event;
	import tto.datas.BoosterItem;
	import tto.datas.CardItem;
	import tto.datas.PotionItem;
	import tto.datas.Save;
	import tto.Game;
	import tto.net.Socket;
	import tto.utils.Assets;
	import tto.utils.i18n;
	import tto.utils.SoundManager;
	import tto.utils.tools;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class RematchPanel extends Panel {
		protected var _params:Object;
		protected var rematchBtn:Button;
		protected var exitBtn:Button;
		protected var rewardList:GroupedList;
		
		public function RematchPanel(params:Object) {
			super();
			
			SoundManager.playSound('se_gs.scd_162', true);
			
			_params = params;
			
			this.width = 370;
			title = i18n.gettext(_params.label);
			footerFactory = rematchFooter;
			
			var VL:VerticalLayout = new VerticalLayout();
			VL.gap = 8;
			VL.horizontalAlign = VerticalLayout.HORIZONTAL_ALIGN_CENTER;
			this.layout = VL;
			
			rewardList = new GroupedList();
			rewardList.width = this.width - 16;
			rewardList.isSelectable = false;
			rewardList.styleName = GroupedList.ALTERNATE_STYLE_NAME_INSET_GROUPED_LIST;
			
			var collection:Array = [];
			var rLHCC:Array = [];
			rLHCC.push({label: _params.XP, icon: new Image(Assets.manager.getTexture('XP'))});
			rLHCC.push({label: _params.MGP, icon: new Image(Assets.manager.getTexture('PGS'))});
			if (params.items && params.items.length > 0) {
				for each (var item:Object in params.items) {
					rLHCC.push({label: i18n.gettext(item.name), icon: item.icon});
				}
			}
			collection.push({header: i18n.gettext('STR_REWARDS'), children: rLHCC});
			if (params.achievements.length > 0) {
				collection.push({header: i18n.gettext('STR_ACHIEVEMENTS'), children: params.achievements});
			}
			
			rewardList.dataProvider = new HierarchicalCollection(collection);
			addChild(rewardList);
			
			var seekRematchLabel:Label = new Label();
			seekRematchLabel.text = i18n.gettext('STR_SEEK_REMATCH_ASK');
			addChild(seekRematchLabel);
		}
		
		protected function rematchFooter():Header {
			var footer:Header = new Header();
			rematchBtn = new Button();
			rematchBtn.label = i18n.gettext('STR_REMATCH');
			rematchBtn.isEnabled = false;
			if (_params.NPC && uint(_params.NPC.matchFee) <= uint(Game.PROFILE_DATAS.MGP)) {
				rematchBtn.isEnabled = true;
			} else if (Boolean(_params.HOST) && uint(Game.MATCHES[_params.ID].gils) <= uint(Game.PROFILE_DATAS.MGP)) {
				rematchBtn.isEnabled = true;
			}
			rematchBtn.addEventListener(Event.TRIGGERED, rematchBtnHandler);
			
			exitBtn = new Button();
			exitBtn.label = i18n.gettext('STR_QUIT');
			exitBtn.addEventListener(Event.TRIGGERED, exitBtnHandler);
			
			footer.leftItems = new <DisplayObject>[exitBtn];
			footer.rightItems = new <DisplayObject>[rematchBtn];
			
			return footer;
		}
		
		protected function exitBtnHandler(e:Event):void {
			SoundManager.fadeSoundChannel(SoundManager._playingChannel, 75, 0);
			if (_params.ID) {
				
				Socket.send('<leave room="' + _params.ID + '" />');
				dispatchEventWith('gotoScreen', true, 'PVP_SCREEN');
			} else
				dispatchEventWith('gotoScreen', true, 'PVE_SCREEN');
		}
		
		protected function rematchBtnHandler(e:Event):void {
			if (_params.NPC) {
				Game.PROFILE_DATAS.STARTED_MATCHES += 1;
				Game.PROFILE_DATAS.PVE_MATCHES += 1;
				Game.PROFILE_DATAS.MGP -= _params.NPC.matchFee;
				Save.save(Game.PROFILE_DATAS);
				this.dispatchEventWith('pve_free_mode', true, {RULES: _params.NPC.gameRules, BLUE_CARDS: [], RED_CARDS: [], _NPC: _params.NPC});
			} else {
				Game.PROFILE_DATAS.STARTED_MATCHES += 1;
				Game.PROFILE_DATAS.PVP_MATCHES += 1;
				Game.PROFILE_DATAS.MGP -= int(Game.MATCHES[_params.ID].gils);
				Save.save(Game.PROFILE_DATAS);
				this.dispatchEventWith('pvp_free_mode', true, {ID: _params.ID, HOST: _params.HOST, RULES: _params.RULES, BLUE_PLAYER_NAME: Game.PROFILE_DATAS.USERNAME, BLUE_CARDS: [], RED_CARDS: [], RED_PLAYER_NAME: String(Game.MATCHES[_params.ID].opponent)});
			}
		}
		
		override public function dispose():void {
			if (rematchBtn && rematchBtn.hasEventListener(Event.TRIGGERED)) rematchBtn.removeEventListeners(Event.TRIGGERED);
			exitBtn.removeEventListener(Event.TRIGGERED, exitBtnHandler);
			tools.purge(this)
			super.dispose();
		}
	}

}