/* JS added to offline pages */
if (typeof jQuery != 'undefined') {
  (function($) {
    $(document).ready(function() {
      
      // Disable all links
      $('a').removeAttr('onclick').click(function() {
        var href = $(this).attr("href");
        // Allow file urls
        if ((/^[.][.][/]access[/]content/).test(href) ) {
            return true;
        }
        alert("Not available in offline mode");
        return false;
      });
      // Disable all form submits.
      $( 'form' ).submit(function( event ) {
        alert("Not available in offline mode");
        event.preventDefault();
      });
      
// Enable things that work offline.      
      
      // Home link
      $('a.icon-sakai-iframe-site').unbind('click').click(function() {
          document.location = '../home/index.htm';
          return false;
      });
      // assignments link
      $('a.icon-sakai-assignment-grades').unbind('click').click(function() {
        document.location = '../assignments/index.htm';
        return false;
      });
      // tests link
      $('a.icon-sakai-samigo').unbind('click').click(function() {
        document.location = '../samigo/index.htm';
        return false;
      });
      // resources link
      $('a.icon-sakai-resources').unbind('click').click(function() {
        document.location = '../resources/index.htm';
        return false;
      });
      // Roster link
      $('a.icon-sakai-site-roster').unbind('click').click(function() {
        document.location = '../roster/index.htm';
        return false;
      });
      // Syllabus link
      $('a.icon-sakai-syllabus').unbind('click').click(function() {
        document.location = '../syllabus/index.htm';
        return false;
      });
/*
      $("a[href^='../access/content/']").each(function() {
        var href = $(this).attr("href");
        alert("href=" + href);
        return true;
      });
*/
      // Enable links marked with offline-link class.
      $("a.offline-link").unbind('click');

      $('#mastLogin').html('This is an offline archive of this course.  Only links in Red will work. NOTE: Works best in Firefox due to Javascript sandbox rules.');
    });
  })(jQuery);
}
