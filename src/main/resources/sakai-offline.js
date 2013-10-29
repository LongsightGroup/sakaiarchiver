/* JS added to offline pages */
if (typeof jQuery != 'undefined') {
  (function($) {
    $(document).ready(function() {
      $('a').click(function() {
        alert("Not available in offline mode");
        return false;
      });
    });
  })(jQuery);
}
