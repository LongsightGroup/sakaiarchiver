/* JS added to offline pages */
var thisSite = new RegExp("^https?:\/\/" + siteHost + "\/","i");

if (typeof jQuery != 'undefined') {
  (function($) {
    $(document).ready(function() {
      
      // Mark code allowed links as offline-links.
      $('a').each(function() {
        var href = $(this).attr("href");
        // Allow file urls
        if ((/^[.][.][/]access[/]content/).test(href) ) {
            $(this).addClass("offline-link");
        }
        if ((/^[.][.][/]fileNotFound.htm/).test(href) ) {
          $(this).addClass("offline-link").addClass('file-not-found');
        }
        // Allow any urls to external sites.
        if ( (/^https?:\/\//i).test(href) &&  ! thisSite.test(href) ) {
          $(this).addClass("offline-link");
        }
        // Allow mail to urls:
        if ( (/^mailto:/i).test(href) ) {
          $(this).addClass("offline-link");
        }
      });
      
      // Disallow all links unless they are allowed.
      $('a').removeAttr('onclick').click(function() {
        var href = $(this).attr("href");
        // Allow file urls
        if ((/^[.][.][/]access[/]content/).test(href) ) {
            return true;
        }
        if ((/^[.][.][/]fileNotFound.htm/).test(href) ) {
          alert("Sakai returned a 'File not found error' for this file. " +
          		  "It could not be included in the archive.");
          return false;
        }
        // Allow any urls to external sites.
        if ( (/^https?:\/\//i).test(href) &&  ! thisSite.test(href) ) {
          return true;
        }
        // Allow mail to urls:
        if ( (/^mailto:/i).test(href) ) {
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
      // forums link
      $('a.icon-sakai-forums').unbind('click').click(function() {
        document.location = '../forums/index.htm';
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
      // Gradebook link
      $('a.icon-sakai-gradebook-tool').unbind('click').click(function() {
        document.location = '../gradebook/index.htm';
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
      $("a.offline-link").not('.file-not-found').unbind('click');

      $('#mastLogin').html('This is an offline archive of this course.  Only links in Red will work. NOTE: Works best in Firefox due to Javascript sandbox rules.');
    });
  })(jQuery);
}
