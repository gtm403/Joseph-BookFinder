package com.example.bookfinder

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.bookfinder.ui.theme.BookFinderTheme
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


data class BookResponse(
    @Json(name = "items") val items: List<BookItem>?
)

data class BookItem(
    @Json(name = "id") val id: String,
    @Json(name = "volumeInfo") val volumeInfo: VolumeInfo
)

data class VolumeInfo(
    @Json(name = "title") val title: String,
    @Json(name = "authors") val authors: List<String>?,
    @Json(name = "publishedDate") val publishedDate: String?,
    @Json(name = "pageCount") val pageCount: Int?,
    @Json(name = "publisher") val publisher: String?,
    @Json(name = "imageLinks") val imageLinks: ImageLinks?
)


data class ImageLinks(
    @Json(name = "thumbnail") val thumbnail: String
)

object RetrofitInstance {
    private const val BASE_URL = "https://www.googleapis.com/books/v1/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val api: GoogleBooksApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleBooksApi::class.java)
    }
}

interface GoogleBooksApi {
    @GET("volumes")
    suspend fun searchBooks(
        @Query("q") query: String
    ): BookResponse
}

class BookViewModel : ViewModel() {
    private val _bookList = MutableStateFlow<List<BookItem>>(emptyList())
    val bookList: StateFlow<List<BookItem>> = _bookList

    fun searchBooks(query: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.api.searchBooks(query)
                _bookList.value = response.items ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                _bookList.value = emptyList()
            }
        }
    }

    fun getBookById(id: String): BookItem? {
        return _bookList.value.find { it.id == id }
    }
}


@Composable
fun BookApp(viewModel: BookViewModel) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "bookList") {
        composable("bookList") {
            BookListScreen(viewModel) { book ->
                navController.navigate("bookDetails/${book.id}")
            }
        }
        composable("bookDetails/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId")
            BookDetailsScreen(bookId, viewModel)
        }
    }
}


@Composable
fun BookListScreen(
    viewModel: BookViewModel,
    onBookClick: (BookItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by title or author") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.searchBooks(searchQuery) },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Search")
        }

        Spacer(modifier = Modifier.height(16.dp))

        val bookList by viewModel.bookList.collectAsState()

        LazyColumn {
            items(bookList) { book ->
                BookItem(book = book, onClick = { onBookClick(book) })
            }
        }
    }
}


@Composable
fun BookItem(book: BookItem, onClick: () -> Unit) {
    val imageUrl = book.volumeInfo.imageLinks?.thumbnail?.replace("http://", "https://")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Book Cover",
                modifier = Modifier.size(60.dp),
                onError = { error ->
                    Log.e("AsyncImage", "Failed to load image: ${error.result.throwable}")
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Text("No Image", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = book.volumeInfo.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.volumeInfo.authors?.joinToString(", ") ?: "Unknown author",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
        }
    }
}


@Composable
fun BookDetailsScreen(bookId: String?, viewModel: BookViewModel) {
    val book = bookId?.let { viewModel.getBookById(it) }

    if (book != null) {
        Scaffold(
            content = { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Book Cover Image
                        AsyncImage(
                            model = book.volumeInfo.imageLinks?.thumbnail?.replace("http://", "https://"),
                            contentDescription = "Book Cover",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))


                        Text(
                            text = book.volumeInfo.title,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))


                        Text(
                            text = "Authors: ${book.volumeInfo.authors?.joinToString(", ") ?: "Unknown"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))


                        if (!book.volumeInfo.publisher.isNullOrEmpty()) {
                            Text(
                                text = "Publisher: ${book.volumeInfo.publisher}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }


                        if (!book.volumeInfo.publishedDate.isNullOrEmpty()) {
                            Text(
                                text = "Published Date: ${book.volumeInfo.publishedDate}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }


                        if (book.volumeInfo.pageCount != null) {
                            Text(
                                text = "Page Count: ${book.volumeInfo.pageCount}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        )
    } else {

        Scaffold(
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Book not found", modifier = Modifier.padding(16.dp))
                }
            }
        )
    }
}



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BookFinderTheme {
                val viewModel: BookViewModel = viewModel()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BookApp(viewModel)
                }
            }
        }
    }
}








