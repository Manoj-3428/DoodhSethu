# DoodhSethu - Login & Signup Screens

This Android application implements beautiful login and signup screens using Jetpack Compose, designed specifically for tablet devices.

## Features

### Design Implementation
- **Figma Design**: Based on the provided Figma design with exact color scheme and layout
- **Tablet Optimized**: Responsive design optimized for tablet screens
- **Material Design 3**: Uses latest Material Design 3 components
- **Custom Typography**: Implements Anton font for the main title

### UI Components
- **Gradient Background**: Beautiful blue gradient background matching the design
- **Card-based Layout**: Clean card layout with shadow effects
- **Form Fields**: 
  - Username field with user icon
  - Email field with email icon (register mode only)
  - Password field with lock icon and show/hide toggle
- **Toggle Buttons**: Switch between Login and Register modes
- **Action Buttons**: Styled buttons with proper shadows and rounded corners
- **Decorative Elements**: Circular decorative elements matching the design

### Technical Features
- **Jetpack Compose**: Modern declarative UI framework
- **State Management**: Proper state handling for form fields and mode switching
- **Vector Drawables**: Custom icons created as Vector Drawables for scalability
- **Custom Colors**: Brand-specific color scheme implemented
- **Responsive Design**: Adapts to different tablet screen sizes

## File Structure

```
app/src/main/
├── java/com/example/doodhsethu/
│   ├── MainActivity.kt              # Main activity with AuthApp composable
│   ├── AuthScreens.kt              # Login/Signup screen implementations
│   └── ui/theme/
│       ├── Color.kt                # Custom color definitions
│       ├── Type.kt                 # Typography with Anton font
│       └── Theme.kt                # Material 3 theme
├── res/
│   ├── drawable/
│   │   ├── ic_user.xml            # User icon
│   │   ├── ic_email.xml           # Email icon
│   │   ├── ic_lock.xml            # Lock icon
│   │   ├── ic_eye.xml             # Eye icon (show password)
│   │   ├── ic_eye_slash.xml       # Eye slash icon (hide password)
│   │   ├── bg_circle_large.png    # Large background circle
│   │   └── bg_circle_small.png    # Small background circle
│   └── font/
│       └── anton_regular.ttf      # Anton font file
```

## Color Scheme

- **Primary Blue**: #004E89
- **Secondary Blue**: #1A659E
- **Light Blue**: #C3E5FF
- **Background Blue**: #E4F3FF
- **Text Blue**: #E6F4FF
- **White**: #FFFFFF

## How to Run

1. Open the project in Android Studio
2. Ensure you have the latest Android SDK and build tools
3. Connect a tablet device or use tablet emulator
4. Click "Run" to build and install the app

## Design Notes

The implementation closely follows the Figma design with:
- Exact color matching
- Proper spacing and typography
- Decorative circular elements
- Form field styling with icons
- Responsive layout for tablet screens
- Smooth transitions between login and register modes

## Future Enhancements

- Add form validation
- Implement actual authentication logic
- Add loading states
- Include error handling
- Add animations for mode switching
- Implement biometric authentication 
 