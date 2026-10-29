using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Windows.Forms;

internal static class Bootstrap
{
    private const string AppVersion = "1.0.0";

    [STAThread]
    private static void Main()
    {
        string exePath = Process.GetCurrentProcess().MainModule.FileName;
        string exeDir = Path.GetDirectoryName(exePath);
        string log = Path.Combine(exeDir, "yml-bootstrap.log");
        try
        {
            string target = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "YML", "app-" + AppVersion);
            try
            {
                Prepare(target);
            }
            catch (UnauthorizedAccessException)
            {
                try
                {
                    target = Path.Combine(Path.GetTempPath(), "YML", "app-" + AppVersion);
                    Prepare(target);
                }
                catch (UnauthorizedAccessException)
                {
                    target = Path.Combine(exeDir, "YML-runtime", "app-" + AppVersion);
                    Prepare(target);
                }
            }

            string appExe = Path.Combine(target, "YML.exe");
            if (!File.Exists(appExe)) throw new FileNotFoundException("YML.exe not found", appExe);

            string arguments = "--launcher-dir=\"" + exeDir + "\" --portable";
#if ADMIN
            arguments += " --admin";
#endif
            Process.Start(new ProcessStartInfo(appExe, arguments)
            {
                UseShellExecute = false,
                WorkingDirectory = target
            });
        }
        catch (Exception error)
        {
            try
            {
                File.AppendAllText(log, DateTime.Now.ToString("s") + " " + error + Environment.NewLine);
            }
            catch
            {
            }
            MessageBox.Show("YML 启动失败：" + error.Message + "\n\n详细信息：" + log,
                "YML", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }

    private static void Prepare(string target)
    {
        string marker = Path.Combine(target, ".ready");
        if (File.Exists(marker))
        {
            return;
        }
        if (Directory.Exists(target))
        {
            Directory.Delete(target, true);
        }
        Directory.CreateDirectory(target);
        using (Stream resource = Assembly.GetExecutingAssembly().GetManifestResourceStream("app.zip"))
        {
            if (resource == null) throw new InvalidOperationException("embedded app.zip is missing");
            using (MemoryStream memory = new MemoryStream())
            {
                resource.CopyTo(memory);
                memory.Position = 0;
                using (ZipArchive archive = new ZipArchive(memory, ZipArchiveMode.Read))
                {
                    string targetFull = Path.GetFullPath(target) + Path.DirectorySeparatorChar;
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        string destination = Path.GetFullPath(Path.Combine(target, entry.FullName));
                        if (!destination.StartsWith(targetFull, StringComparison.OrdinalIgnoreCase)) continue;
                        if (string.IsNullOrEmpty(entry.Name))
                        {
                            Directory.CreateDirectory(destination);
                        }
                        else
                        {
                            Directory.CreateDirectory(Path.GetDirectoryName(destination));
                            entry.ExtractToFile(destination, true);
                        }
                    }
                }
            }
        }
        File.WriteAllText(marker, "ok");
    }
}
