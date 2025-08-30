# DoodhSethu - Milk Collection Management System
## Developer Documentation

### **Project Overview**
DoodhSethu is an Android application built with Jetpack Compose for managing milk collection from farmers. The app provides features for adding farmers, recording daily milk collections, generating reports, and exporting data to Excel format.

### **Technology Stack**

#### **Frontend (UI Layer)**
- **Framework**: Android Jetpack Compose
- **Language**: Kotlin
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **UI Components**: Material Design 3, Custom Components
- **State Management**: Kotlin StateFlow, MutableStateFlow
- **Navigation**: Compose Navigation
- **Icons**: Custom vector drawables, Material Icons

#### **Backend (Data Layer)**
- **Local Database**: Room Database (SQLite)
- **Cloud Database**: Firebase Firestore
- **Authentication**: Firebase Authentication
- **File Storage**: Firebase Storage (for photos)
- **Real-time Sync**: Firestore Real-time listeners
- **Offline Support**: Room Database with sync capabilities

### **Project Structure**

```
DoodhSethu/
├── app/src/main/java/com/example/doodhsethu/
│   ├── MainActivity.kt                    # Main entry point
│   ├── AuthScreens.kt                     # Authentication screens
│   ├── components/                        # Reusable UI components
│   │   ├── AuthForm.kt
│   │   ├── AuthTextField.kt
│   │   ├── NetworkStatusIndicator.kt
│   │   └── OfflineSyncIndicator.kt
│   ├── data/                             # Data layer
│   │   ├── models/                       # Database entities & models
│   │   │   ├── AppDatabase.kt           # Room database
│   │   │   ├── User.kt                  # User entity
│   │   │   ├── Farmer.kt                # Farmer entity
│   │   │   ├── MilkCollection.kt        # Milk collection entity
│   │   │   └── DailyMilkCollection.kt   # Daily summary entity
│   │   ├── network/                      # Network utilities
│   │   └── repository/                   # Repository pattern
│   │       ├── UserRepository.kt
│   │       ├── FarmerRepository.kt
│   │       ├── MilkCollectionRepository.kt
│   │       └── DailyMilkCollectionRepository.kt
│   ├── ui/                              # UI layer
│   │   ├── screens/                     # Screen composables
│   │   │   ├── DashboardScreen.kt
│   │   │   ├── AddFarmerScreen.kt
│   │   │   ├── AddMilkCollectionScreen.kt
│   │   │   ├── MilkReportsScreen.kt
│   │   │   ├── UserReportsScreen.kt
│   │   │   └── FarmerDetailsScreen.kt
│   │   ├── viewmodels/                  # ViewModels
│   │   │   ├── AuthViewModel.kt
│   │   │   ├── FarmerViewModel.kt
│   │   │   ├── MilkCollectionViewModel.kt
│   │   │   └── MilkReportViewModel.kt
│   │   └── theme/                       # UI theming
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   └── utils/                           # Utility classes
│       ├── NetworkUtils.kt              # Network monitoring
│       ├── AutoSyncManager.kt           # Background sync
│       ├── PhotoUploadManager.kt        # Photo handling
│       └── FatTableUtils.kt             # Fat calculation
```

### **Key Features & Implementation**

#### **1. Authentication System**
- **Location**: `AuthScreens.kt`, `AuthViewModel.kt`
- **Features**: Login/Register (Register temporarily disabled)
- **Storage**: Firebase Authentication + Local Room database

#### **2. Farmer Management**
- **Location**: `AddFarmerScreen.kt`, `FarmerViewModel.kt`
- **Features**: Add farmers with photos, view farmer list
- **Photo Storage**: Firebase Storage with local caching

#### **3. Milk Collection**
- **Location**: `AddMilkCollectionScreen.kt`, `MilkCollectionViewModel.kt`
- **Features**: Record AM/PM collections, fat testing, price calculation
- **Data Flow**: Local Room → Firestore sync

#### **4. Reports & Analytics**
- **Location**: `MilkReportsScreen.kt`, `UserReportsScreen.kt`
- **Features**: 
  - Daily/monthly reports
  - Excel export with UTF-8 encoding
  - Notification system for downloads
  - Custom date range selection

#### **5. Excel Export System**
- **Location**: `MilkReportViewModel.kt` (exportToExcelSuspend method)
- **Features**:
  - CSV generation with proper UTF-8 BOM
  - Dates on Y-axis, Farmer IDs on X-axis
  - Zero values for missing data
  - File download with notification
  - Click to open functionality

### **Database Schema**

#### **Room Database Tables**
- **User**: User authentication and profile data
- **Farmer**: Farmer information with photos
- **MilkCollection**: Individual milk collection records
- **DailyMilkCollection**: Daily aggregated data
- **BillingCycle**: Billing period management
- **FatTable**: Fat percentage calculations

#### **Firestore Collections**
- **users**: User profiles
- **farmers**: Farmer data with photo URLs
- **milkCollections**: Real-time collection data
- **dailyCollections**: Aggregated daily data

### **Sync Architecture**

#### **Online Mode**
- Real-time Firestore listeners
- Automatic background sync
- Photo upload to Firebase Storage
- Conflict resolution (server wins)

#### **Offline Mode**
- Local Room database operations
- Queued sync operations
- Photo caching
- Network status monitoring

### **Key Utilities**

#### **NetworkUtils.kt**
- Network connectivity monitoring
- Online/offline state management
- Automatic sync triggering

#### **AutoSyncManager.kt**
- Background data synchronization
- Conflict resolution
- Error handling and retry logic

#### **PhotoUploadManager.kt**
- Photo compression and upload
- Local caching
- Progress tracking

### **Permissions Required**
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### **Configuration Files**
- **google-services.json**: Firebase configuration
- **file_paths.xml**: FileProvider paths for sharing
- **AndroidManifest.xml**: App permissions and FileProvider setup

### **Development Guidelines**

#### **Adding New Features**
1. Create data models in `data/models/`
2. Add repository methods in `data/repository/`
3. Create ViewModel in `ui/viewmodels/`
4. Build UI in `ui/screens/`
5. Update sync logic if needed

#### **Database Changes**
1. Update Room entities
2. Increment database version
3. Add migration scripts
4. Update Firestore schema

#### **UI Components**
- Use Material Design 3 components
- Follow existing color scheme (PrimaryBlue, BackgroundBlue)
- Use Poppins font family
- Implement proper error handling and loading states

### **Testing**
- Unit tests in `test/` directory
- UI tests in `androidTest/` directory
- Manual testing for sync scenarios

### **Deployment**
- Firebase project configuration required
- Google Services JSON file needed
- Release signing configuration
- ProGuard rules for optimization

### **Recent Updates**

#### **Excel Export Feature**
- Added comprehensive Excel export functionality
- UTF-8 encoding with BOM for proper character display
- Notification system with click-to-open
- Permission handling for Android 10+ compatibility
- Zero value handling for missing data

#### **UI Improvements**
- Decimal formatting consistency across screens
- Farmer sorting by ID
- Submit button text size optimization
- Register screen temporarily disabled

#### **Data Accuracy**
- Fixed rounding issues in milk reports
- Proper total calculation using rounded values
- Enhanced data validation and error handling

### **Color Scheme**
- **Primary Blue**: #004E89
- **Secondary Blue**: #1A659E
- **Light Blue**: #C3E5FF
- **Background Blue**: #E4F3FF
- **Text Blue**: #E6F4FF
- **White**: #FFFFFF

### **How to Run**

1. Open the project in Android Studio
2. Ensure you have the latest Android SDK and build tools
3. Add your `google-services.json` file to the app directory
4. Configure Firebase project settings
5. Connect a device or use emulator
6. Click "Run" to build and install the app

---

This documentation provides a comprehensive overview for new developers to understand the project structure, technologies used, and how to extend the application. 
 