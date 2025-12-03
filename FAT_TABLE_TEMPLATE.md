# FAT Table Import Template

## File Format Requirements

The file (Excel or CSV) must contain **exactly 3 columns** with the following structure:

> **Supported formats:** .xlsx, .csv

### Column Headers (Row 1):
- **Column A**: From Value
- **Column B**: To Value  
- **Column C**: Price

### Data Format:
- All values must be **positive numbers**
- **From Value** must be less than **To Value** for each row
- No overlapping ranges allowed
- No empty rows between data

## File Upload

You can now upload Excel files directly:

1. **Excel files** (.xlsx) - Upload directly
2. **CSV files** (.csv) - Also supported
3. **Click the upload icon** (☁️) in the FAT Table screen
4. **Select your file** from your device
5. **The system will automatically detect and parse** the file format

### Example Data Format:

| From Value | To Value | Price |
|------------|----------|-------|
| 3.0        | 3.5      | 23.0  |
| 3.5        | 4.0      | 24.0  |
| 4.0        | 4.5      | 25.0  |
| 4.5        | 5.0      | 26.0  |
| 5.0        | 5.5      | 27.0  |
| 5.5        | 6.0      | 28.0  |

### Example CSV Format:
```
From Value,To Value,Price
3.0,3.5,23.0
3.5,4.0,24.0
4.0,4.5,25.0
4.5,5.0,26.0
5.0,5.5,27.0
5.5,6.0,28.0
```

## Validation Rules:

1. **Exactly 3 columns** - No more, no less
2. **Valid numeric values** - All values must be numbers
3. **Positive values** - No negative numbers allowed
4. **Valid ranges** - From Value < To Value
5. **No overlaps** - Ranges cannot overlap with each other
6. **File format** - Must be .xlsx or .csv file

## Error Messages:

- "Row X has Y columns. Expected exactly 3 columns"
- "Please select a valid Excel or CSV file (.xlsx, .csv)"
- "XLS files (.xls) are not supported. Please save as XLSX (.xlsx) format."
- "Row X: All values must be positive numbers"
- "Row X: 'From Value' must be less than 'To Value'"
- "Error: Overlapping fat ranges detected"

## How to Use:

1. Open the FAT Table screen
2. Click the **upload icon** (☁️) in the top-right corner
3. Select your Excel or CSV file
4. The system will validate and import the data
5. Success/error messages will be shown via toast notifications
