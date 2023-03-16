; ModuleID = 'moudle'
source_filename = "moudle"

@sort_arr = global [5 x i32] [i32 48, i32 27, i32 60, i32 64, i32 67]

define i32 @f(i32* %0) {
fEntry:
  %arr1 = alloca i32*, align 8
  store i32* %0, i32** %arr1, align 8
  %arr11 = load i32*, i32** %arr1, align 8
  %arr12 = getelementptr i32, i32* %arr11, i32 0
  %arr13 = load i32, i32* %arr12, align 4
  ret i32 %arr13
  ret i32 0
}

define i32 @combine(i32* %0, i32 %1, i32* %2, i32 %3) {
combineEntry:
  %arr1 = alloca i32*, align 8
  store i32* %0, i32** %arr1, align 8
  %arr1_length = alloca i32, align 4
  store i32 %1, i32* %arr1_length, align 4
  %arr2 = alloca i32*, align 8
  store i32* %2, i32** %arr2, align 8
  %arr2_length = alloca i32, align 4
  store i32 %3, i32* %arr2_length, align 4
  %arr11 = load i32*, i32** %arr1, align 8
  %arr12 = getelementptr i32, i32* %arr11, i32 0
  store i32 9, i32* %arr12, align 4
  %arr = alloca [1 x i32], align 4
  %arr3 = getelementptr [1 x i32], [1 x i32]* %arr, i32 0, i32 0
  store i32 8, i32* %arr3, align 4
  %arr14 = load i32*, i32** %arr1, align 8
  %arr15 = getelementptr i32, i32* %arr14, i32 0
  %arr16 = load i32, i32* %arr15, align 4
  %arr7 = getelementptr [1 x i32], [1 x i32]* %arr, i32 0, i32 0
  store i32 %arr16, i32* %arr7, align 4
  %arr18 = getelementptr i32*, i32** %arr1, i32 0
  %arr19 = load i32*, i32** %arr18, align 8
  %f = call i32 @f(i32* %arr19)
  ret i32 %f
  ret i32 0
}

define i32 @main() {
mainEntry:
  %a = alloca [2 x i32], align 4
  %a1 = getelementptr [2 x i32], [2 x i32]* %a, i32 0, i32 0
  store i32 1, i32* %a1, align 4
  %a2 = getelementptr [2 x i32], [2 x i32]* %a, i32 0, i32 1
  store i32 5, i32* %a2, align 4
  %b = alloca [3 x i32], align 4
  %b3 = getelementptr [3 x i32], [3 x i32]* %b, i32 0, i32 0
  store i32 1, i32* %b3, align 4
  %b4 = getelementptr [3 x i32], [3 x i32]* %b, i32 0, i32 1
  store i32 4, i32* %b4, align 4
  %b5 = getelementptr [3 x i32], [3 x i32]* %b, i32 0, i32 2
  store i32 14, i32* %b5, align 4
  %a6 = getelementptr [2 x i32], [2 x i32]* %a, i32 0, i32 0
  %b7 = getelementptr [3 x i32], [3 x i32]* %b, i32 0, i32 0
  %combine = call i32 @combine(i32* %a6, i32 2, i32* %b7, i32 3)
  ret i32 %combine
  ret i32 0
}
