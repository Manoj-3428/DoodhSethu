# DoodhSethu - Dairy Management App 🥛

**DoodhSethu** is a comprehensive dairy management application built with Android Kotlin and Jetpack Compose. It helps dairy farmers and milk collection centers manage their daily operations efficiently.

## 🌟 Features

### 👨‍🌾 Farmer Management
- Add, edit, and delete farmer profiles
- Store farmer details (name, phone, location, photo)
- Cascade deletion - all related data is removed when farmer is deleted

### 🥛 Milk Collection
- Daily milk collection tracking (Morning & Evening)
- Automatic amount calculation based on fat percentage
- Real-time fat table management
- Duplicate entry prevention

### 💰 Billing & Payments
- Automated billing cycle management
- Payment tracking and history
- Billing summaries and reports
- Date range filtering

### 📊 Reports & Analytics
- Milk collection reports
- Farmer performance analytics
- Billing cycle summaries
- Date-wise filtering options

### 🔄 Data Synchronization
- **Offline-first architecture** - Works without internet
- **Real-time Firestore sync** - Automatic cloud backup
- **Smart conflict resolution** - Handles data conflicts intelligently
- **Background sync** - No loading indicators, smooth UX

### 🔐 Authentication & Security
- User authentication and session management
- Secure data storage
- Auto-login with session persistence

## 🛠️ Technical Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM with Repository Pattern
- **Local Database:** Room
- **Cloud Database:** Firebase Firestore
- **Authentication:** Firebase Auth
- **Image Handling:** Local storage with cloud sync
- **Network:** Retrofit + OkHttp

## 📱 Screenshots

*(Add screenshots of your app here)*

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Kotlin 1.8+
- Android SDK 21+
- Firebase project setup

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/Manoj-3428/DoodhSethu.git
   cd DoodhSethu
   ```

2. **Setup Firebase**
   - Create a Firebase project
   - Add your `google-services.json` to `app/` directory
   - Enable Firestore and Authentication

3. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   ```

## 📂 Project Structure

```
app/
├── src/main/java/com/example/doodhsethu/
│   ├── data/
│   │   ├── models/          # Data models and Room entities
│   │   └── repository/      # Repository classes for data management
│   ├── ui/
│   │   ├── screens/         # Compose UI screens
│   │   ├── viewmodels/      # ViewModels for MVVM architecture
│   │   └── theme/           # App theming and colors
│   ├── utils/               # Utility classes
│   └── MainActivity.kt      # Main entry point
```

## 🔧 Key Features Implementation

### Offline-First Architecture
- Local Room database as single source of truth
- Background Firestore synchronization
- Intelligent conflict resolution
- Network state monitoring

### Smart Data Management
- Automatic duplicate prevention
- Cascade deletion for data integrity
- Optimized database queries
- Efficient memory usage

### User Experience
- No loading indicators for smooth UX
- Instant local operations
- Background cloud sync
- Responsive Material Design UI

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📧 Contact

**Manoj Kumar** - [GitHub](https://github.com/Manoj-3428)

Project Link: [https://github.com/Manoj-3428/DoodhSethu](https://github.com/Manoj-3428/DoodhSethu)

## 🙏 Acknowledgments

- Built with Android Jetpack Compose
- Firebase for backend services
- Material Design for UI components
- Room database for local storage

---

⭐ **If you find this project helpful, please give it a star!** ⭐