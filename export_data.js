const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Initialize Firebase Admin SDK with your project
admin.initializeApp({
  projectId: 'doodhsetu-af429'
});

const db = admin.firestore();

async function exportCollection(collectionPath) {
  try {
    console.log(`📥 Exporting: ${collectionPath}`);
    
    const snapshot = await db.collection(collectionPath).get();
    const documents = [];
    
    snapshot.forEach(doc => {
      documents.push({
        id: doc.id,
        ...doc.data()
      });
    });
    
    return documents;
  } catch (error) {
    console.log(`⚠️  Collection ${collectionPath} not found or empty`);
    return [];
  }
}

async function exportSubCollections(parentPath, parentId) {
  try {
    console.log(`📥 Exporting sub-collections for: ${parentPath}/${parentId}`);
    
    const snapshot = await db.collection(parentPath).doc(parentId).listCollections();
    const subCollections = {};
    
    for (const subCollection of snapshot) {
      const subSnapshot = await db.collection(parentPath).doc(parentId).collection(subCollection.id).get();
      const documents = [];
      
      subSnapshot.forEach(doc => {
        documents.push({
          id: doc.id,
          ...doc.data()
        });
      });
      
      subCollections[subCollection.id] = documents;
    }
    
    return subCollections;
  } catch (error) {
    console.log(`⚠️  No sub-collections found for: ${parentPath}/${parentId}`);
    return {};
  }
}

async function exportAllData() {
  console.log('🚀 Starting Firestore data export for doodhsetu-af429...\n');
  
  const allData = {};
  
  // Export top-level collections
  const topLevelCollections = ['users', 'fat_table', 'counters'];
  
  for (const collection of topLevelCollections) {
    const data = await exportCollection(collection);
    allData[collection] = data;
    
    // If this is users collection, also export sub-collections for each user
    if (collection === 'users' && data.length > 0) {
      allData[`${collection}_subcollections`] = {};
      
      for (const user of data) {
        const subCollections = await exportSubCollections('users', user.userId || user.id);
        if (Object.keys(subCollections).length > 0) {
          allData[`${collection}_subcollections`][user.userId || user.id] = subCollections;
        }
      }
    }
  }
  
  // Create exports directory
  const exportsDir = path.join(__dirname, 'firestore_backup');
  if (!fs.existsSync(exportsDir)) {
    fs.mkdirSync(exportsDir, { recursive: true });
  }
  
  // Save complete backup
  const backupPath = path.join(exportsDir, `doodhsethu_backup_${new Date().toISOString().split('T')[0]}.json`);
  fs.writeFileSync(backupPath, JSON.stringify(allData, null, 2));
  
  // Save individual collection files
  for (const [collectionName, data] of Object.entries(allData)) {
    if (Array.isArray(data)) {
      const filePath = path.join(exportsDir, `${collectionName}.json`);
      fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
    } else {
      const filePath = path.join(exportsDir, `${collectionName}.json`);
      fs.writeFileSync(filePath, JSON.stringify(data, null, 2));
    }
  }
  
  // Create summary
  const summary = {
    exportDate: new Date().toISOString(),
    projectId: 'doodhsetu-af429',
    totalCollections: Object.keys(allData).length,
    statistics: {}
  };
  
  for (const [collection, data] of Object.entries(allData)) {
    if (Array.isArray(data)) {
      summary.statistics[collection] = `${data.length} documents`;
    } else {
      summary.statistics[collection] = `${Object.keys(data).length} sub-collections`;
    }
  }
  
  const summaryPath = path.join(exportsDir, 'export_summary.json');
  fs.writeFileSync(summaryPath, JSON.stringify(summary, null, 2));
  
  console.log('\n✅ Export completed successfully!');
  console.log(`📁 Backup files saved in: ${exportsDir}`);
  console.log(`📊 Complete backup: ${backupPath}`);
  console.log(`📋 Summary: ${summaryPath}`);
  
  console.log('\n📈 Export Statistics:');
  for (const [collection, stats] of Object.entries(summary.statistics)) {
    console.log(`  ${collection}: ${stats}`);
  }
}

// Run the export
exportAllData().catch(error => {
  console.error('❌ Export failed:', error);
  process.exit(1);
});
