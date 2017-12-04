package tto.screens 
{
	import feathers.controls.GroupedList;
	import feathers.controls.Header;
	import feathers.controls.Label;
	import feathers.controls.Panel;
	import feathers.controls.PickerList;
	import feathers.controls.ProgressBar;
	import feathers.controls.Screen;
	import feathers.controls.Scroller;
	import feathers.controls.TextArea;
	import feathers.data.HierarchicalCollection;
	import starling.display.Button;
	import starling.display.DisplayObject;
	import starling.display.Image;
	import starling.events.Event;
	import tto.controls.AvatarChooser;
	import tto.controls.MGPLabel;
	import tto.controls.RoundChart;
	import tto.datas.Achievements;
	import tto.datas.Level;
	import tto.datas.Save;
	import tto.display.AchievementIcon;
	import tto.display.UserBar;
	import tto.Game;
	import tto.utils.Assets;
	import tto.utils.i18n;
	
	/**
	 * ...
	 * @author Mao
	 */
	public class profileScreen extends Screen 
	{
		private const ranks:Vector.<uint>  = new <uint>[0, 25, 50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600, 51200, 102400, 204800, 409600, 819200, 1638400, 3276800, 6553600, 13107200, 26214400];
		
		private var winsLabel:Label;
		private var defeatsLabel:Label;
		private var drawsLabel:Label;
		private var forfeitsLabel:Label;
		private var avatarPicker:PickerList;
		private var _list:GroupedList;
		private var MGP:MGPLabel;
		private var userAvatar:Image;
		private var backButton:Button;
		private var panelStatistics:Panel;
		private var totalLabel:Label;
		private var panelSuccess:Panel;
		private var successList:GroupedList;
		private var userBar:UserBar;
		private var profileJSON:TextArea;
		private var levelBar:ProgressBar;
		private var username:Label;
		private var mgpBoonIcon:Image;
		private var xpBoonIcon:Image;
		
		public function profileScreen() 
		{
			super();
			
		}
		
		override protected function draw():void
		{
			super.draw();
			
			userBar.x = stage.width - userBar.width - 16;
			userBar.y = 16;
			
			panelStatistics.width = stage.width/2 - 16;
			panelStatistics.height = stage.height - 206;
			panelSuccess.x = panelStatistics.x + panelStatistics.width + 8;
			panelSuccess.width = stage.width - panelSuccess.x -8;
			panelSuccess.height = stage.height - 206;
			
			_list.width = panelStatistics.width - 16;
			mgpBoonIcon.x = username.x + username.width + 16;
			xpBoonIcon.x = mgpBoonIcon.x + mgpBoonIcon.width;
		}
		
		override protected function initialize():void {
			super.initialize();
			
			userBar = new UserBar('PROFILE');
			addChild(userBar);
			
			panelStatistics = new Panel();
			panelStatistics.title = i18n.gettext("STR_PROFILE");
			panelStatistics.headerFactory = headerFactory;
			panelStatistics.x = 8;
			panelStatistics.y = 96;
			panelStatistics.horizontalScrollPolicy = Panel.SCROLL_POLICY_OFF;
			addChild(panelStatistics);
			
			var avatarSelector:AvatarChooser = new AvatarChooser(Assets.manager.getTexture(Game.PROFILE_DATAS.AVATAR_ID), 100);
			avatarSelector.x = avatarSelector.y = 8;
			panelStatistics.addChild(avatarSelector);
			
			//var username:TextField = new TextField(300, 48, Game.PROFILE_DATAS.USERNAME, 'Eurostile', 24, 0x333333, true);
			username = new Label();
			username.text = Game.PROFILE_DATAS.USERNAME;
			username.styleName = Label.ALTERNATE_STYLE_NAME_HEADING;
			username.x = 96 + 16;
			username.y = 8;
			panelStatistics.addChild(username);
			
			// BOONS = {MGP:0, XP:0, LUCK:0}
			mgpBoonIcon = new Image(Assets.manager.getTexture('mgp_boost_icon'));
			mgpBoonIcon.visible = (uint(Game.PROFILE_DATAS.BOONS.MGP) > 0)
			mgpBoonIcon.touchable = false;
			panelStatistics.addChild(mgpBoonIcon);
			
			xpBoonIcon = new Image(Assets.manager.getTexture('xp_boost_icon'));
			xpBoonIcon.visible = (uint(Game.PROFILE_DATAS.BOONS.XP) > 0)
			xpBoonIcon.touchable = false;
			panelStatistics.addChild(xpBoonIcon);
			
			Game.PROFILE_DATAS.LEVEL = Level.xpToLevel(Game.PROFILE_DATAS.XP);
			
			var level:Label = new Label();
			level.text = i18n.gettext('STR_LEVEL')+' : '+Game.PROFILE_DATAS.LEVEL;
			level.styleName = Label.ALTERNATE_STYLE_NAME_DETAIL;
			level.x = 100 + 16;
			level.y = 48;
			panelStatistics.addChild(level);
			
			levelBar = new ProgressBar();
			levelBar.x = 100 + 16;
			levelBar.y = 64;
			levelBar.direction = ProgressBar.DIRECTION_HORIZONTAL;
			levelBar.minimum = Level.steps[Game.PROFILE_DATAS.LEVEL - 1];
			levelBar.maximum = Level.steps[Game.PROFILE_DATAS.LEVEL];
			levelBar.value = Game.PROFILE_DATAS.XP;
			panelStatistics.addChild(levelBar);
			
			winsLabel = new Label();
			winsLabel.styleName = 'GREY';
			winsLabel.text = Game.PROFILE_DATAS.STATS.WINS;
			
			defeatsLabel = new Label();
			defeatsLabel.styleName = 'GREY';
			defeatsLabel.text = Game.PROFILE_DATAS.STATS.DEFEATS;
			
			drawsLabel = new Label();
			drawsLabel.styleName = 'GREY';
			drawsLabel.text = Game.PROFILE_DATAS.STATS.DRAWS;
			
			forfeitsLabel = new Label();
			forfeitsLabel.styleName = 'GREY';
			forfeitsLabel.text = Game.PROFILE_DATAS.STATS.FORFEITS;
			
			totalLabel = new Label();
			totalLabel.styleName = 'GREY';
			totalLabel.text = String(uint(tto.Game.PROFILE_DATAS.STATS.WINS) + uint(tto.Game.PROFILE_DATAS.STATS.DEFEATS) + uint(tto.Game.PROFILE_DATAS.STATS.DRAWS) /*+uint(tto.Game.PROFILE_DATAS.STATS.FORFEITS)*/);
			
			MGP = new MGPLabel( Game.PROFILE_DATAS.MGP);
			
			_list = new GroupedList();
			_list.x = 8;
			_list.y = 100 + 8;
			_list.isSelectable = false;
			_list.styleName = GroupedList.ALTERNATE_STYLE_NAME_INSET_GROUPED_LIST;
			_list.verticalScrollPolicy = GroupedList.SCROLL_POLICY_OFF;
			_list.dataProvider = new HierarchicalCollection(
			[
				{
					children:
					[
						{ label: i18n.gettext('STR_WINS'), accessory: winsLabel },
						{ label: i18n.gettext('STR_DEFEATS'), accessory: defeatsLabel },
						{ label: i18n.gettext('STR_DRAWS'), accessory: drawsLabel },
						{ label: i18n.gettext('STR_FORFEITS'), accessory: forfeitsLabel },
						{ label: i18n.gettext('STR_TOTAL'), accessory: totalLabel },
						{ label: i18n.gettext('STR_MGP'), accessory: MGP },
					]
				},
			]);
			panelStatistics.addChild(_list);
			
			
			var _total:uint = uint(tto.Game.PROFILE_DATAS.STATS.WINS) + uint(tto.Game.PROFILE_DATAS.STATS.DEFEATS) + uint(tto.Game.PROFILE_DATAS.STATS.DRAWS);
			var winsDegreeRatio:Number = uint(tto.Game.PROFILE_DATAS.STATS.WINS) * 360 / _total;
			var defeatsDegreeRatio:Number = uint(tto.Game.PROFILE_DATAS.STATS.DEFEATS) * 360 / _total;
			var drawDegreeRatio:Number = uint(tto.Game.PROFILE_DATAS.STATS.DRAWS) * 360 / _total;
			
			var segments:Array = new Array(
			{color:0xb5c338, ratio:drawDegreeRatio, name:"Draws" },
			{color:0x2dbeeb, ratio:winsDegreeRatio, name:"Wins" },
			{color:0xe8202d, ratio:defeatsDegreeRatio, name:"Defeats" }
			);
			var chart:RoundChart = new RoundChart(segments, 36, String(_total), i18n.gettext('STR_MATCHES'));
			chart.x = 380;
			chart.y = 0;
			panelStatistics.addChild(chart);
			
			
			panelSuccess = new Panel();
			panelSuccess.title = i18n.gettext("STR_ACHIEVEMENTS_LIST");
			panelSuccess.y = 96;
			addChild(panelSuccess);
			
			successList = new GroupedList();
			successList.x = 8;
			successList.y = 8;
			successList.isSelectable = false;
			
			var achievements:Array = [];
			var achievementDescription:Label
			var achId:String
			for (achId in Game.PROFILE_DATAS.ACHIEVEMENTS) {
				achievementDescription = new Label();
				achievementDescription.styleName = Label.ALTERNATE_STYLE_NAME_DETAIL;
				achievementDescription.text = i18n.gettext(Achievements.list[achId].label + '_DESC');
				achievements.push( {
					unlockDate:Game.PROFILE_DATAS.ACHIEVEMENTS[achId],
					label:i18n.gettext(Achievements.list[achId].label),
					icon:new AchievementIcon(Achievements.list[achId].iconID),
					accessory:achievementDescription
				});
			}
			achievements.sortOn(['unlockDate', 'label'], [Array.DESCENDING, Array.CASEINSENSITIVE]);
			successList.itemRendererProperties.accessoryPosition = "bottom";
			successList.dataProvider = new HierarchicalCollection(
			[
				{children:achievements},
			]);
			panelSuccess.addChild(successList);
		}
		
		private function avatarPicker_ChangeHandler(e:Event):void {
			userAvatar.texture = avatarPicker.selectedItem.texture;
			userAvatar.readjustSize();
		}
		
		private function backButton_triggeredHandler(e:Event):void {
			dispatchEventWith('gotoScreen', false, 'DASHBOARD');
		}
		
		
		private function headerFactory():Header {
			var header:Header = new Header();
			backButton = new Button(Assets.manager.getTexture('action_back_up'), '', Assets.manager.getTexture('action_back_down'));
			backButton.addEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			header.leftItems =  new <DisplayObject>[backButton];
			return header;
		}
		
		override public function dispose():void {
			backButton.removeEventListener(Event.TRIGGERED, backButton_triggeredHandler);
			super.dispose();
		}
	}

}