﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Net.NetworkInformation;
using System.Management;
using Microsoft.Win32;

namespace NetInterfaceManager {
    class Program {

        private static RegistryKey registryKey = Registry.CurrentUser.OpenSubKey("Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", true);

        static void Main(string[] args) {
            if (args.Length == 0) return;
            switch (args[0]) {
                case "getinterfaces": {
                    writeInterfaces();
                    break;
                }
                case "getinterfaceconfig": {
                    getInterfaceConfig(args);
                    break;
                }
                case "hasStaticIP": {
                    hasStaticIP(args);
                    break;
                }
                case "applyconfig": {
                    applyConfig(args);
                    break;
                }
                case "resetconfig": {
                    resetConfig();
                    break;
                }
                case "isproxyenabled": {
                    isProxyEnabled();
                    break;
                }
                case "setproxystate": {
                    setProxyState(args);
                    break;
                }
            }
        }

        static void writeInterfaces() {
            NetworkInterface[] interfaces = NetworkInterface.GetAllNetworkInterfaces();
            Console.WriteLine(interfaces.Length);
            foreach (NetworkInterface inter in interfaces) {
                Console.WriteLine(inter.Description);
            }
        }

        static void getInterfaceConfig(string[] args) {
            string netInterface = args[1];

            ManagementClass objMC = new ManagementClass("Win32_NetworkAdapterConfiguration");
            ManagementObjectCollection objMOC = objMC.GetInstances();

            bool found = false;
            string ipAddress = null;
            string subnetMask = null;
            string gateway = null;
            string favDNS = null;
            string auxDNS = null;

            foreach (ManagementObject objMO in objMOC) {
                if (!((string)objMO["Description"]).Equals(netInterface)) continue;
                if (!((bool)objMO["IPEnabled"])) {
                    Console.WriteLine("error notconnected");
                    return;
                }
                found = true;

                // Check if the interface is connected to a network
                NetworkInterface[] networkInterfaces = NetworkInterface.GetAllNetworkInterfaces();
                foreach (NetworkInterface inter in networkInterfaces) {
                    if (!inter.Description.Equals(netInterface)) continue;
                    if (!inter.OperationalStatus.ToString().Equals("Up")) {
                        Console.WriteLine("error notconnected");
                        return;
                    }
                }

                ipAddress = ((string[])objMO["IPAddress"])[0];
                subnetMask = ((string[])objMO["IPSubnet"])[0];
                try {
                    gateway = ((string[])objMO["DefaultIPGateway"])[0];
                } catch (NullReferenceException e) {}
                try {
                    string[] dnsSearchOrder = (string[])objMO["DNSServerSearchOrder"];
                    if (dnsSearchOrder.Length > 0) {
                        favDNS = dnsSearchOrder[0];
                    }
                    if (dnsSearchOrder.Length > 1) {
                        auxDNS = dnsSearchOrder[1];
                    }
                } catch (NullReferenceException e) {}
                break;
            }

            if (!found) {
                Console.WriteLine("error notconnected");
                return;
            }

            Console.WriteLine("success");
            Console.WriteLine(ipAddress);
            Console.WriteLine(subnetMask);
            Console.WriteLine(gateway);
            Console.WriteLine(favDNS);
            Console.WriteLine(auxDNS);
        }

        static void hasStaticIP(string[] args) {
            string netInterface = args[1];

            ManagementClass objMC = new ManagementClass("Win32_NetworkAdapterConfiguration");
            ManagementObjectCollection objMOC = objMC.GetInstances();

            foreach (ManagementObject objMO in objMOC) {
                if (!((string)objMO["Description"]).Equals(netInterface)) continue;
                if (!((bool)objMO["IPEnabled"])) {
                    Console.WriteLine("error notconnected");
                    return;
                }

                bool hasStaticIP = !((bool)objMO["DHCPEnabled"]);
                Console.WriteLine("success");
                Console.WriteLine(hasStaticIP);
                return;
            }

            Console.WriteLine("error notconnected");
        }

        static void applyConfig(string[] args) {
            if (args.Length < 4) {
                Console.WriteLine("error notenoughargs");
                return;
            }
            string netInterface = args[1];
            string ip = args[2];
            string subnetMask = args[3];

            ManagementClass objMC = new ManagementClass("Win32_NetworkAdapterConfiguration");
            ManagementObjectCollection objMOC = objMC.GetInstances();
            bool success = false;

            // Search for the net interface to modify
            foreach (ManagementObject objMO in objMOC) {
                if (!((bool)objMO["IPEnabled"])) continue;
                if (!((string)objMO["Description"]).Equals(netInterface)) continue;

                // Check if the interface is connected to a network
                NetworkInterface[] networkInterfaces = NetworkInterface.GetAllNetworkInterfaces();
                foreach (NetworkInterface inter in networkInterfaces) {
                    if (!inter.Description.Equals(netInterface)) continue;
                    if (!inter.OperationalStatus.ToString().Equals("Up")) {
                        Console.WriteLine("error notconnected");
                        return;
                    }
                }

                // Set new IP and subnet mask
                ManagementBaseObject newIp = objMO.GetMethodParameters("EnableStatic");
                newIp["IPAddress"] = new string[] { ip };
                newIp["SubnetMask"] = new string[] { subnetMask };

                // Set new gateway or default gateway if not provided
                ManagementBaseObject newGateway = objMO.GetMethodParameters("SetGateways");
                if (args.Length >= 5) {
                    string gateway = args[4];
                    newGateway["DefaultIPGateway"] = new string[] { gateway };
                } else {
                    newGateway["DefaultIPGateway"] = new string[] { "0.0.0.0" };
                }
                newGateway["GatewayCostMetric"] = new int[] { 1 };

                // Set new DNS
                if (args.Length >= 7) {
                    string favDNS = args[5];
                    string auxDNS = args[6];
                    ManagementBaseObject newDNS = objMO.GetMethodParameters("SetDNSServerSearchOrder");
                    newDNS["DNSServerSearchOrder"] = new string[] { favDNS, auxDNS };
                    objMO.InvokeMethod("SetDNSServerSearchOrder", newDNS, null);
                }

                objMO.InvokeMethod("EnableStatic", newIp, null);
                objMO.InvokeMethod("SetGateways", newGateway, null);

                success = true;
                break;
            }

            if (success) {
                Console.WriteLine("success");
            } else {
                Console.WriteLine("error notfound");
            }
        }

        static void resetConfig() {
            ManagementClass objMC = new ManagementClass("Win32_NetworkAdapterConfiguration");
            ManagementObjectCollection objMOC = objMC.GetInstances();

            foreach (ManagementObject objMO in objMOC) {
                if (!((bool)objMO["IPEnabled"])) continue;
                // Reset IP
                objMO.InvokeMethod("EnableDHCP", null);
                // Reset DNS
                ManagementBaseObject newDNS = objMO.GetMethodParameters("SetDNSServerSearchOrder");
                newDNS["DNSServerSearchOrder"] = null;
                objMO.InvokeMethod("SetDNSServerSearchOrder", newDNS, null);
            }

            Console.WriteLine("success");
        }

        static void isProxyEnabled() {
            Console.WriteLine((int)registryKey.GetValue("ProxyEnable"));
        }

        static void setProxyState(string[] args) {
            registryKey.SetValue("ProxyEnable", Int32.Parse(args[1]));
        }
    }
}
