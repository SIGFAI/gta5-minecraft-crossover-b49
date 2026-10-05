Add-Type @'
using System; using System.Runtime.InteropServices;
public static class W { [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr FindWindow(string c, string t);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
[DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
[DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, System.Text.StringBuilder s, int n); }
'@
$h = [W]::FindWindow('grcWindow', $null)
$fg = [W]::GetForegroundWindow(); $sb = New-Object Text.StringBuilder 100; [void][W]::GetWindowText($fg, $sb, 100)
"foreground before: $($sb.ToString())"
[void][W]::SetForegroundWindow($h)
Start-Sleep -Milliseconds 500
$fg = [W]::GetForegroundWindow(); $sb = New-Object Text.StringBuilder 100; [void][W]::GetWindowText($fg, $sb, 100)
"foreground after: $($sb.ToString())"
