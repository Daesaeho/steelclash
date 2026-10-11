# Run in your own PowerShell window. Keys are stored in your Windows user environment, never in this repository.
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$agentKeyForm = New-Object System.Windows.Forms.Form
$agentKeyForm.Text = 'Steelclash assistant API keys'
$agentKeyForm.ClientSize = New-Object System.Drawing.Size(520, 245)
$agentKeyForm.StartPosition = 'CenterScreen'
$agentKeyForm.FormBorderStyle = 'FixedDialog'
$agentKeyForm.MaximizeBox = $false
$agentKeyForm.MinimizeBox = $false

$agentKeyNote = New-Object System.Windows.Forms.Label
$agentKeyNote.Text = 'Enter either key or both. Blank fields keep existing keys. Stored for your Windows account; restart Codex afterward.'
$agentKeyNote.Location = New-Object System.Drawing.Point(18, 15)
$agentKeyNote.Size = New-Object System.Drawing.Size(480, 44)
$agentKeyForm.Controls.Add($agentKeyNote)

$agentKeyFields = @()
$agentKeyRows = @(
    @{ Label = 'Mercury / Inception API key'; Name = 'INCEPTION_API_KEY'; Y = 65 },
    @{ Label = 'Google AI Studio API key'; Name = 'GEMINI_API_KEY'; Y = 125 }
)
foreach ($agentKeyRow in $agentKeyRows) {
    $agentKeyLabel = New-Object System.Windows.Forms.Label
    $agentKeyLabel.Text = $agentKeyRow.Label
    $agentKeyLabel.Location = New-Object System.Drawing.Point(18, $agentKeyRow.Y)
    $agentKeyLabel.Size = New-Object System.Drawing.Size(480, 20)
    $agentKeyInput = New-Object System.Windows.Forms.TextBox
    $agentKeyInput.Location = New-Object System.Drawing.Point(18, ($agentKeyRow.Y + 22))
    $agentKeyInput.Size = New-Object System.Drawing.Size(480, 24)
    $agentKeyInput.UseSystemPasswordChar = $true
    $agentKeyInput.Tag = $agentKeyRow.Name
    $agentKeyForm.Controls.Add($agentKeyLabel)
    $agentKeyForm.Controls.Add($agentKeyInput)
    $agentKeyFields += $agentKeyInput
}

$agentKeySave = New-Object System.Windows.Forms.Button
$agentKeySave.Text = 'Save keys'
$agentKeySave.Location = New-Object System.Drawing.Point(290, 192)
$agentKeySave.Size = New-Object System.Drawing.Size(100, 32)
$agentKeySave.Add_Click({
    try {
        foreach ($agentKeyField in $agentKeyFields) {
            $agentKeyValue = $agentKeyField.Text.Trim()
            if ($agentKeyValue.Length -gt 0) {
                [Environment]::SetEnvironmentVariable([string]$agentKeyField.Tag, $agentKeyValue, 'User')
                [Environment]::SetEnvironmentVariable([string]$agentKeyField.Tag, $agentKeyValue, 'Process')
            }
            $agentKeyField.Clear()
        }
        [System.Windows.Forms.MessageBox]::Show('Saved. Restart Codex to load the keys. No API calls were made.', 'Steelclash') | Out-Null
        $agentKeyForm.Close()
    } catch {
        [System.Windows.Forms.MessageBox]::Show('Windows could not save the keys. Nothing was sent to an API.', 'Steelclash') | Out-Null
    }
})
$agentKeyCancel = New-Object System.Windows.Forms.Button
$agentKeyCancel.Text = 'Cancel'
$agentKeyCancel.Location = New-Object System.Drawing.Point(400, 192)
$agentKeyCancel.Size = New-Object System.Drawing.Size(100, 32)
$agentKeyCancel.Add_Click({ $agentKeyForm.Close() })
$agentKeyForm.Controls.Add($agentKeySave)
$agentKeyForm.Controls.Add($agentKeyCancel)
$agentKeyForm.AcceptButton = $agentKeySave
$agentKeyForm.CancelButton = $agentKeyCancel
[void]$agentKeyForm.ShowDialog()
$agentKeyForm.Dispose()
