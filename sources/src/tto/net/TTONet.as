package {
	import flash.events.Event;
	import flash.net.NetworkInfo;
	import flash.net.NetworkInterface;
	import flash.net.InterfaceAddress;
	import flash.desktop.NativeApplication;
	
	public class TTONet {
		private static var networkInfo:NetworkInfo;
		private static var interfaces:Vector.<NetworkInterface>;

		public function TTONet() {
			networkInfo = NetworkInfo.networkInfo;
			interfaces = networkInfo.findInterfaces();

			// constructor code
			NativeApplication.nativeApplication.idleThreshold = 30;
			NativeApplication.nativeApplication.addEventListener(Event.USER_IDLE, function(event:Event) { 
			    trace("Idle"); 
			});
			NativeApplication.nativeApplication.addEventListener(Event.USER_PRESENT, function(event:Event) { 
			    trace("Present"); 
			});
			NativeApplication.nativeApplication.addEventListener(Event.NETWORK_CHANGE, function(event:Event) { 
			    trace("NETWORK_CHANGE : "+event); 
			});

			trace( "Interface count: " + interfaces.length );
			for each (var interfaceObj:NetworkInterface in interfaces) {
				trace( "\nname: "             + interfaceObj.name );
				trace( "display name: "     + interfaceObj.displayName );
				trace( "mtu: "                 + interfaceObj.mtu );
				trace( "active?: "             + interfaceObj.active );
				trace( "parent interface: " + interfaceObj.parent );
				trace( "hardware address: " + interfaceObj.hardwareAddress );
				if (interfaceObj.subInterfaces != null) {
					trace( "# subinterfaces: " + interfaceObj.subInterfaces.length );
				}
				trace("# addresses: "     + interfaceObj.addresses.length );
				for each (var address:InterfaceAddress in interfaceObj.addresses) {
					trace( "  type: "           + address.ipVersion );
					trace( "  address: "         + address.address );
					trace( "  broadcast: "         + address.broadcast );
					trace( "  prefix length: "     + address.prefixLength );
				}
				trace("----------------------------------------------------");
			}
		}
	}
}