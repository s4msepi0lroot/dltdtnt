// [ModuleInitializer] ships with .NET 5+. This project targets netstandard2.1, so we polyfill
// the attribute ourselves. The C# compiler (LangVersion latest) recognizes it by name/namespace.
namespace System.Runtime.CompilerServices;

[AttributeUsage(AttributeTargets.Method, Inherited = false)]
internal sealed class ModuleInitializerAttribute : Attribute
{
}
